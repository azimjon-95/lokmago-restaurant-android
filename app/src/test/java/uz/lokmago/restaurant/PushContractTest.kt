package uz.lokmago.restaurant

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import uz.lokmago.restaurant.data.remote.dto.DeviceRegisterRequest
import uz.lokmago.restaurant.data.remote.dto.DeviceUnregisterRequest

/** lakmago-server `/api/panel/devices` (controllers/mobileDevices.js) aynan shu JSON'ni kutadi. */
class PushContractTest {
    // same switches as the app's Retrofit converter (di/AppModule): null fields are not sent
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test fun `register body matches the server schema`() {
        assertEquals("""{"token":"abc","platform":"android"}""", json.encodeToString(DeviceRegisterRequest("abc")))
    }

    @Test fun `register body carries device details and permission state when known`() {
        assertEquals("""{"token":"abc","platform":"android","deviceId":"d-1","appVersion":"1.4.0","notificationsEnabled":false}""",
            json.encodeToString(DeviceRegisterRequest("abc", "android", "d-1", "1.4.0", false)))
    }

    @Test fun `ack body matches the server schema`() {
        assertEquals("""{"token":"abc","orderId":"6716f0a1b2c3d4e5f6a7b8c9","attempt":2,"event":"received"}""",
            json.encodeToString(uz.lokmago.restaurant.data.remote.dto.DeviceAckRequest("abc", "6716f0a1b2c3d4e5f6a7b8c9", 2, "received")))
    }

    @Test fun `unregister body carries only the token`() {
        assertEquals("""{"token":"abc"}""", json.encodeToString(DeviceUnregisterRequest("abc")))
    }
}
