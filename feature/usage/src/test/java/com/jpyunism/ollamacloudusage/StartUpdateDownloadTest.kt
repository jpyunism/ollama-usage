package com.jpyunism.ollamacloudusage

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartUpdateDownloadTest {
    @Before fun setup() { Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `startUpdateDownload delega en el lambda inyectado, no recursa`() {
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { prefs.getInt(any(), any()) } returns 60
        val repo = mockk<UsageRepository>(relaxed = true)
        every { repo.hasAuth() } returns false
        val updateRepo = mockk<UpdateRepository>(relaxed = true)
        every { updateRepo.shouldCheck() } returns false
        var delegated = false
        val vm = UsageViewModel(
            prefs = prefs,
            repository = repo,
            updateRepository = updateRepo,
            historyStoreProvider = { mockk(relaxed = true) },
            apiKeyValidator = mockk(relaxed = true),
            onUpdateDownload = { delegated = true },
        )
        val info = UpdateInfo(
            versionName = "9.9.9",
            downloadUrl = "https://example.com/a.apk",
            sha256 = null,
        )
        vm.startUpdateDownload(info)
        assertTrue("el lambda inyectado debe recibir la llamada", delegated)
    }
}
