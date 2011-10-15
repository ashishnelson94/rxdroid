package at.caspase.rxdroid.db;

import java.sql.Timestamp;
import java.util.Date;

import at.caspase.rxdroid.Fraction;
import at.caspase.rxdroid.util.Hasher;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Represents a dose intake by the user.
 *
 * Each database entry will consist of an id of the drug that was taken, a timestamp
 * representing the time the user marked the dose as 'taken' in the app, the dose-time, the <em>scheduled</em>
 * date (note that this may differ from the date represented by the timestamp. Assume for
 * example that the user takes a drug scheduled for the night at 1 minute past midnight.),
 *
 * @author Joseph Lehner
 */
@DatabaseTable(tableName = "intake")
public class Intake extends Entry
{
	private static final long serialVersionUID = -9158847314588407608L;
	
	@DatabaseField(foreign = true)
	private Drug drug;

	@DatabaseField
	private java.util.Date date;

	@DatabaseField
	private java.util.Date timestamp;

	@DatabaseField
	private int doseTime;
	
	@DatabaseField(dataType = DataType.SERIALIZABLE)
	private Fraction dose;

	public Intake() {}

	public Intake(Drug drug, Date date, int doseTime, Fraction dose)
	{
		this.drug = drug;
		this.date = date;
		this.timestamp = new Timestamp(System.currentTimeMillis());
		this.doseTime = doseTime;
		this.dose = dose;
	}
	
	public int getDrugId() {
		return drug.getId();
	}
	
	public Drug getDrug() {
		return Database.findDrug(getDrugId());			
	}
	
	public Fraction getDose() {
		return dose;
	}

	public Date getDate() {
		return new Date(date.getTime());
	}

	public Date getTimestamp() {
		return new Timestamp(timestamp.getTime());
	}

	public int getDoseTime() {
		return doseTime;
	}

	@Override
	public int hashCode()
	{
		final Hasher hasher = new Hasher();

		hasher.hash(drug);
		hasher.hash(date);
		hasher.hash(timestamp);
		hasher.hash(doseTime);

		return hasher.getHashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof Intake))
			return false;

		final Intake other = (Intake) o;

		if(this.doseTime != other.doseTime)
			return false;

		if(!this.timestamp.equals(other.timestamp))
			return false;

		if(!this.date.equals(other.date))
			return false;

		if(this.getDrugId() != other.getDrugId())
			return false;
		
		if(!this.dose.equals(other.dose))
			return false;

		return true;
	}

	@Override
	public String toString() {
		return drug.getName() + ": date=" + date + ", doseTime=" + doseTime + ", dose=" + dose;
	}
}