package uz.lokmago.restaurant.data.remote

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import uz.lokmago.restaurant.courier.CDeviceReq
import uz.lokmago.restaurant.courier.CMeReq
import uz.lokmago.restaurant.data.remote.dto.DeviceRegisterRequest

/**
 * Regression: the server rejected EVERY Android push registration with 400 «Token yoki platforma noto'g'ri», because kotlinx dropped
 * `platform = "android"` (a default value) from the request. These tests use the app's real [ApiJson] and real request classes.
 */
class ApiJsonTest {
    private val token = "fcmTokenXXXXXXXXXXXXXXXXXXXX:C4DXdl"

    @Test fun `restaurant push registration always sends platform`() {
        val s = ApiJson.encodeToString(DeviceRegisterRequest(token, "android", "uuid-1", "1.0.0", true))
        assertTrue(s, s.contains("\"platform\":\"android\""))
        assertTrue(s, s.contains("\"token\":\"$token\""))
    }

    @Test fun `null optional fields are not sent - the server's optional() rejects null`() {
        val s = ApiJson.encodeToString(DeviceRegisterRequest(token))
        assertTrue(s, s.contains("\"platform\":\"android\""))
        assertFalse(s, s.contains("deviceId"))
        assertFalse(s, s.contains("null"))
    }

    @Test fun `courier push registration always sends platform`() {
        val s = ApiJson.encodeToString(CDeviceReq(token, "android", "1.0.0"))
        assertTrue(s, s.contains("\"platform\":\"android\""))
    }

    @Test fun `courier clearing all districts sends an empty list, not nothing`() {
        assertEquals("{\"workDistricts\":[]}", ApiJson.encodeToString(CMeReq(emptyList())))
    }

    @Test fun `unknown fields from a newer server are ignored`() {
        val r = ApiJson.decodeFromString<CMeReq>("{\"workDistricts\":[\"chust\"],\"somethingNew\":1}")
        assertEquals(listOf("chust"), r.workDistricts)
    }
}
