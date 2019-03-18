package com.threathunter.nebula.common.util;

import com.threathunter.config.CommonDynamicConfig;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 */
public class SystemClockTest {

    @Test
    public void testCustomerClock() throws InterruptedException {
        CommonDynamicConfig.getInstance().addOverrideProperty(ConstantsUtil.CUSTOMER_CLOCK, true);

        SystemClock.getCurrentTimestamp();
        Assert.assertTrue(SystemClock.getCurrentTimestamp() < 2000);

        SystemClock.syncCustomerTimestamp(System.currentTimeMillis() - 2000);
        Assert.assertTrue(System.currentTimeMillis() / 100 - SystemClock.getCurrentTimestamp() / 100 > 10);

        SystemClock.syncCustomerTimestamp(System.currentTimeMillis());
        Thread.sleep(2000);
        Assert.assertTrue(System.currentTimeMillis() / 100 - SystemClock.getCurrentTimestamp() / 100 < 5);
    }

//    @Test
//    public void testSystemClock() throws InterruptedException {
//        CommonDynamicConfig.getInstance().addOverrideProperty(ConstantsUtil.CUSTOMER_CLOCK, false);
//
//        // add to initial, it will take some time
//        SystemClock.getCurrentTimestamp();
//        Assert.assertTrue(System.currentTimeMillis() / 100 - SystemClock.getCurrentTimestamp() / 100 < 5);
//        Thread.sleep(2000);
//        // no use
//
//        SystemClock.syncCustomerTimestamp(System.currentTimeMillis() - 2000);
//        Assert.assertTrue(System.currentTimeMillis() / 100 - SystemClock.getCurrentTimestamp() / 100 < 5);
//    }
}
