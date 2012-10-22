/**
 * Copyright (C) 2011 Joseph Lehner <joseph.c.lehner@gmail.com>
 *
 * This file is part of RxDroid.
 *
 * RxDroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RxDroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RxDroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package at.caspase.rxdroid;

import java.util.Date;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import at.caspase.rxdroid.FractionInput.OnChangedListener;
import at.caspase.rxdroid.db.Database;
import at.caspase.rxdroid.db.Drug;
import at.caspase.rxdroid.db.Entries;
import at.caspase.rxdroid.db.Entry;
import at.caspase.rxdroid.db.Intake;
import at.caspase.rxdroid.db.Schedule;
import at.caspase.rxdroid.util.Util;

public class IntakeDialog extends AlertDialog implements OnChangedListener, Database.OnChangeListener
{
	private static final String TAG = IntakeDialog.class.getName();
	private static final boolean LOGV = true;

	public static final String ARG_DRUG_ID = "drug_id";
	public static final String ARG_DOSE_TIME = "dose_time";
	public static final String ARG_DATE = "date";

	private static final int STATE_DOSE_DISPLAY = 0;
	private static final int STATE_DOSE_EDIT = 1;
	private static final int STATE_INSUFFICIENT_SUPPLIES = 2;

	private Drug mDrug;
	private int mDoseTime;
	private Date mDate;

	private Fraction mDose;
	private int mIntakeCount;

	private int mFlags;

	private TextView mDoseText;
	private TextView mHintText;
	private FractionInput mDoseEdit;
	private TextView mInsufficientSupplyText;

	private TextView mMessageText;

	private int mState;

	private OnShowListener mOnShowListener;

	public static final int FLAG_ALLOW_DOSE_EDIT = 1;

	public IntakeDialog(Context context)
	{
		super(context);

		View view = getLayoutInflater().inflate(R.layout.intake, null);

		view.findViewById(R.id.dose_container).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v)
			{
				setState(STATE_DOSE_EDIT);
			}
		});

		mDoseText = (TextView) view.findViewById(R.id.dose_text);
		mHintText = (TextView) view.findViewById(R.id.dose_hint);
		mDoseEdit = (FractionInput) view.findViewById(R.id.dose_edit);
		mInsufficientSupplyText = (TextView) view.findViewById(R.id.text_insufficient_supplies);

		setView(view);

		setButton(BUTTON_NEGATIVE, getString(android.R.string.cancel), mLocalOnClickListener);
		// The actual listener for this button is added in mLocalOnShowListener!
		setButton(BUTTON_POSITIVE, getString(android.R.string.ok), (OnClickListener) null);

		super.setOnShowListener(mLocalOnShowListener);
		Database.registerEventListener(this);

		// Without this, the setMessage() calls in updateMessage() would be ignored; same
		// goes for title and icon, but we don't set one for now.
		setMessage("");
	}

	public IntakeDialog(final Context context, Drug drug, int doseTime, Date date)
	{
		this(context);
		update(drug, doseTime, date);
	}

	public void setArgs(Bundle args)
	{
		final Drug drug;
		final int drugId = args.getInt(ARG_DRUG_ID, -1);
		if(drugId == -1 || (drug = Drug.find(drugId)) == null)
			throw new IllegalArgumentException();

		final int doseTime = args.getInt(ARG_DOSE_TIME, Schedule.TIME_INVALID);
		final Date date = (Date) args.getSerializable(ARG_DATE);

		if(doseTime == Schedule.TIME_INVALID || date == null)
			throw new IllegalArgumentException();

		update(drug, doseTime, date);
	}

	@Override
	public void setOnShowListener(android.content.DialogInterface.OnShowListener listener) {
		mOnShowListener = listener;
	}

	@Override
	protected void onStop()
	{
		//dismissPopup();
		Database.unregisterEventListener(this);
	}

	@Override
	public void onFractionChanged(FractionInput widget, Fraction oldValue)
	{
		mDose = widget.getValue();
		getButton(BUTTON_POSITIVE).setEnabled(!mDose.isZero());
		mDoseText.setText(mDose.toString());
	}

	@Override
	public void onEntryUpdated(Entry entry, int flags)
	{
		if(entry instanceof Drug && entry.getId() == mDrug.getId())
		{
			mDrug = (Drug) entry;

			if(!hasInsufficientSupplies())
				setState(STATE_DOSE_DISPLAY);
//				dismissPopup();
		}
	}

	@Override
	public void onEntryCreated(Entry entry, int flags) {}

	@Override
	public void onEntryDeleted(Entry entry, int flags)
	{
		if(entry instanceof Drug && entry.getId() == mDrug.getId())
		{
//			if(mPopup != null)
//				dismissPopup();

			if(isShowing())
				dismiss();
		}
	}

	@Override
	public void onBackPressed()
	{
//		if(!isPopupShowing())
//			super.onBackPressed();
//		else
//			dismissPopup();

		if(mState == STATE_INSUFFICIENT_SUPPLIES)
			setState(STATE_DOSE_EDIT);
		else
			super.onBackPressed();
	}

	public void setHtmlMessage(String message) {
		super.setMessage(Html.fromHtml(message));
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		mMessageText = (TextView) findViewById(android.R.id.message);
		if(mMessageText != null)
		{
			mMessageText.setGravity(Gravity.CENTER);
			mMessageText.setTextAppearance(getContext(), android.R.attr.textAppearanceSmall);
		}
	}

	private void update(Drug drug, int doseTime, Date date)
	{
		Database.init();

		mDrug = drug;
		mDoseTime = doseTime;
		mDate = date;

		if(LOGV) Log.v(TAG, "update: doseTime=" + doseTime + ", date=" + date + ", drug=" + drug);

		mIntakeCount = Entries.countIntakes(drug, date, doseTime);

		if(mIntakeCount == 0)
			mDose = drug.getDose(doseTime, date);
		else
			mDose = new Fraction();

		 mDoseText.setText(mDose.toString());
         mDoseEdit.setValue(mDose);
         mDoseEdit.setAutoInputModeEnabled(true);
         mDoseEdit.setOnChangeListener(this);
         mInsufficientSupplyText.setOnClickListener(new View.OnClickListener() {

                 @Override
                 public void onClick(View v)
                 {
                         final Context context = IntakeDialog.this.getContext();
                         final Intent intent = new Intent(context, DrugEditActivity.class);
                         intent.setAction(Intent.ACTION_EDIT);
                         intent.putExtra(DrugEditActivity.EXTRA_DRUG, mDrug);
                         intent.putExtra(DrugEditActivity.EXTRA_FOCUS_ON_CURRENT_SUPPLY, true);

                         context.startActivity(intent);
                 }
         });

         //setTitle(mDrug.getName());
         //setIcon(Util.getDrugIconDrawable(getContext(), mDrug.getIcon()));

         //updateMessage();
	}

	private boolean hasInsufficientSupplies()
	{
		if(mDrug.getRefillSize() == 0)
			return false;

		Fraction supplies = mDrug.getCurrentSupply();
		return supplies.compareTo(mDose) == -1;
	}

	private void addIntakeAndDismiss()
	{
		Intake intake = new Intake(mDrug, mDate, mDoseTime, mDose);
		Database.create(intake);

		Fraction newSupply = mDrug.getCurrentSupply().minus(mDose);
		mDrug.setCurrentSupply(newSupply.isNegative() ? new Fraction() : newSupply);
		Database.update(mDrug, Database.FLAG_DONT_NOTIFY_LISTENERS);

		dismiss();

		Toast.makeText(getContext(), R.string._toast_intake_noted, Toast.LENGTH_SHORT).show();
	}

	private void setupViews()
	{
		boolean doseIsZero = mDose.isZero();

		setState(doseIsZero ? STATE_DOSE_EDIT : STATE_DOSE_DISPLAY);

		getButton(BUTTON_POSITIVE).setText(getString(android.R.string.ok));
		getButton(BUTTON_NEGATIVE).setText(getString(android.R.string.cancel));

		Button b = getButton(BUTTON_POSITIVE);
		b.setEnabled(!doseIsZero);

		updateMessage();
	}

	private void setNonDismissingListener(int button)
	{
		Button b = getButton(button);
		if(b != null)
			b.setOnClickListener(new NonDismissingListener(button));
	}

	private void updateMessage()
	{
		final int msgResId;

		if(mDose.isZero() || (mFlags & FLAG_ALLOW_DOSE_EDIT) != 0)
			msgResId = R.string._msg_intake_unscheduled;
		else
			msgResId = R.string._msg_intake_normal;

		final String drugName = mDrug.getName();
		final String okStr = getContext().getString(android.R.string.ok);

		setHtmlMessage(getContext().getString(msgResId, drugName, okStr));
	}

	private void setState(int state)
	{
		if(mState == state)
			return;

		mState = state;

		// these could be booleans checking for state == STATE_<FOOBAR>, but
		// we might need views that are visibile in more than one state.
		int doseTextVisibility = View.INVISIBLE;
		int doseEditVisibility = View.INVISIBLE;
		int insufficientSupplyTextVisibility = View.INVISIBLE;

		switch(mState)
		{
			case STATE_DOSE_DISPLAY:
				doseTextVisibility = View.VISIBLE;
				break;

			case STATE_DOSE_EDIT:
				doseEditVisibility = View.VISIBLE;
				break;

			case STATE_INSUFFICIENT_SUPPLIES:
				insufficientSupplyTextVisibility = View.VISIBLE;
				break;
		}

		mDoseText.setVisibility(doseTextVisibility);
		mHintText.setVisibility(doseTextVisibility);

		mDoseEdit.setVisibility(doseEditVisibility);
		mInsufficientSupplyText.setVisibility(insufficientSupplyTextVisibility);

		if(state == STATE_INSUFFICIENT_SUPPLIES)
		{
			final Context context = getContext();
			final String okStr = context.getString(android.R.string.ok);
			final String text = context.getString(R.string._msg_footer_insufficient_supplies,
					mDrug.getCurrentSupply(), okStr, mDrug.getName());

			mInsufficientSupplyText.setText(Html.fromHtml(text));
		}
	}

	private String getString(int resId) {
		return getContext().getString(resId);
	}

	private final OnShowListener mLocalOnShowListener = new OnShowListener() {

		@Override
		public void onShow(final DialogInterface dialog)
		{
			setNonDismissingListener(BUTTON_POSITIVE);
			//setNonDismissingListener(BUTTON_NEUTRAL);

			setupViews();

			if(mOnShowListener != null)
				mOnShowListener.onShow(dialog);
		}
	};

	private final OnClickListener mLocalOnClickListener = new OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			if(which == BUTTON_POSITIVE)
			{
				if(hasInsufficientSupplies() && mState != STATE_INSUFFICIENT_SUPPLIES)
					setState(STATE_INSUFFICIENT_SUPPLIES);
				else
					addIntakeAndDismiss();
			}
		}
	};

	private class NonDismissingListener implements View.OnClickListener
	{
		private int mButton;

		public NonDismissingListener(int button) {
			mButton = button;
		}

		@Override
		public void onClick(View v)
		{
			// Allows handling the click in the expected place,
			// while allowing control over whether the dialog
			// should be dismissed or not.
			mLocalOnClickListener.onClick(IntakeDialog.this, mButton);
		}
	}
}
