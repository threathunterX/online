package com.threathunter.nebula.common.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 */
public class LocationHelperTest {
    @Test
    public void testLANLocation() {
        String param = "172.16.0.1";

        Assert.assertEquals("内网", LocationHelper.getLocation(param, ConstantsUtil.LOCATION_CITY));
        Assert.assertEquals("内网", LocationHelper.getLocation(param, ConstantsUtil.LOCATION_PROVINCE));
    }

    @Test
    public void testRealLocation() {
        String param = "202.115.192.1";

        Assert.assertEquals("四川省", LocationHelper.getLocation(param, "province"));
        Assert.assertEquals("成都市", LocationHelper.getLocation(param, "city"));
    }
}
