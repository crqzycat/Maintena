package crqzycat.maintena.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpUtilTest {

    @Test
    void keepsIpv4AsTyped() {
        assertEquals("1.2.3.4", IpUtil.normalize("1.2.3.4"));
        assertEquals("255.255.255.255", IpUtil.normalize("255.255.255.255"));
        assertEquals("1.2.3.4", IpUtil.normalize("  1.2.3.4  "));
    }

    @Test
    void rejectsInvalidIpv4() {
        assertNull(IpUtil.normalize("256.1.1.1"));
        assertNull(IpUtil.normalize("1.2.3"));
        assertNull(IpUtil.normalize("1.2.3.4.5"));
    }

    @Test
    void expandsIpv6() {
        assertEquals("2001:db8:0:0:0:0:0:1", IpUtil.normalize("2001:db8::1"));
        assertEquals("0:0:0:0:0:0:0:1", IpUtil.normalize("::1"));
    }

    @Test
    void differentWaysToWriteTheSameIpv6AreEqual() {
        String expected = IpUtil.normalize("2001:db8::1");

        assertEquals(expected, IpUtil.normalize("2001:DB8::1"));
        assertEquals(expected, IpUtil.normalize("2001:0db8:0000:0000:0000:0000:0000:0001"));
        assertEquals(expected, IpUtil.normalize("[2001:db8::1]"));
    }

    @Test
    void removesIpv6ZoneSuffix() {
        assertEquals("fe80:0:0:0:0:0:0:1", IpUtil.normalize("fe80::1%eth0"));
    }

    @Test
    void ipv4MappedAddressBecomesIpv4() {
        assertEquals("1.2.3.4", IpUtil.normalize("::ffff:1.2.3.4"));
    }

    @Test
    void rejectsInvalidIpv6AndOtherText() {
        assertNull(IpUtil.normalize("2001:db8::zz"));
        assertNull(IpUtil.normalize("1:2:3"));
        assertNull(IpUtil.normalize("localhost"));
        assertNull(IpUtil.normalize("Steve"));
        assertNull(IpUtil.normalize(""));
        assertNull(IpUtil.normalize(null));
    }

    @Test
    void isIpAddressMatchesNormalize() {
        assertTrue(IpUtil.isIpAddress("1.2.3.4"));
        assertTrue(IpUtil.isIpAddress("2001:db8::1"));
        assertFalse(IpUtil.isIpAddress("Steve"));
    }
}
