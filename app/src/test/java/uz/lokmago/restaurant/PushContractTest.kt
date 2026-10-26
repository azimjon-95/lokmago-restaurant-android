package uz.lokmago.restaurant

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import uz.lokmago.restaurant.data.remote.dto.DeviceRegisterRequest
import uz.lokmago.restaurant.data.remote.dto.DeviceUnregisterRequest

/** lakmago-server `/api/panel/devices` (controllers/mobileDevices.js) aynan shu JSON'ni kutadi. */
class PushContractTest {
    private val json = Json { encodeDefaults = true }

    @Test fun `register body matches the server schema`() {
        assertEquals("""{"token":"abc","platform":"android"}""", json.encodeToString(DeviceRegisterRequest("abc")))
    }

    @Test fun `unregister body carries only the token`() {
        assertEquals("""{"token":"abc"}""", json.encodeToString(DeviceUnregisterRequest("abc")))
    }
}
