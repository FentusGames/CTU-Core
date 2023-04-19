package ctu.core.abstracts;

/**
 * @author     Fentus
 * 
 *             The Connection class is an abstract class that provides methods to handle connection data such as
 *             compression, decompression, sending TCP and UDP packets, and converting bytes to packets. The class also
 *             contains a list of acceptable classes that it can check against.
 * @param  <T>
 * @param  <T>
 */
public class User<T> {
	private long connectionID = -1;
	private long userID = -1;
	private T connectionObject = null;

	public void setConnectionID(long connectionID) {
		this.connectionID = connectionID;
	}

	public long getConnectionID() {
		return connectionID;
	}

	public void setUserID(long userID) {
		this.userID = userID;
	}

	public long getUserID() {
		return userID;
	}

	public void setConnectionObject(T connectionObject) {
		this.connectionObject = connectionObject;
	}

	public T getConnectionObject() {
		return connectionObject;
	}
}
