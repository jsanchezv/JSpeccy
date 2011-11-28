package tv.porst.jhexview;

public interface IDataProvider {

	void addListener(final IDataChangedListener hexView);

	byte[] getData();

	byte[] getData(long offset, int length);

	int getDataLength();

	boolean hasData(long start, int length);

	boolean isEditable();

	boolean keepTrying();

	void removeListener(IDataChangedListener listener);

	void setData(long offset, byte[] data);
}
