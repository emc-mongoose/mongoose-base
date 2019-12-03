package com.emc.mongoose.base.data;

import com.github.akurilov.commons.system.SizeInBytes;
import org.junit.Assert;
import org.junit.Test;

public class DataInputTest {

	@Test
	public void testMemoryTypeAllocation()
	throws Exception {
		try(final var dataInput = DataInput.instance(null, "7a42d9c483244167", new SizeInBytes("1"), 1, false)) {
			Assert.assertTrue(dataInput.getLayer(0).isDirect());
		}
		try(final var dataInput = DataInput.instance(null, "7a42d9c483244167", new SizeInBytes("1"), 1, true)) {
			Assert.assertFalse(dataInput.getLayer(0).isDirect());
		}
	}
}
