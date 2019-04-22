package com.emc.mongoose.base.control.run;

public interface Run extends Runnable {

	/**
	 * @return run id
	 * @throws IllegalStateException if not started yet
	 */
	long runId() throws IllegalStateException;

	/** @return user comment for this run */
	String comment();
}
