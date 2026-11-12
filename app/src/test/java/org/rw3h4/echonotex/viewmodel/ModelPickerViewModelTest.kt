package org.rw3h4.echonotex.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalog
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogException

@OptIn(ExperimentalCoroutinesApi::class)
class ModelPickerViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(id: String = "m1", minRamMb: Int = 3072) = ModelCatalogEntry(
        id = id,
        name = "Model $id",
        description = "d",
        url = "https://example.com/$id.gguf",
        filename = "$id.gguf",
        sizeBytes = 1_000_000_000L,
        sha256 = "a".repeat(64),
        quant = "Q4_K_M",
        minRamMb = minRamMb,
        contextDefault = 2048,
        chatTemplateId = "qwen2",
    )

    private fun viewModel(
        loadCatalog: () -> ModelCatalog,
        deviceRamMb: Long,
    ) = ModelPickerViewModel(
        loadCatalog = loadCatalog,
        readDeviceRamMb = { deviceRamMb },
        ioDispatcher = mainDispatcher,
    )

    @Test
    fun successfulLoadEmitsSuccessWithBuckets() = runTest(mainDispatcher) {
        val catalog = ModelCatalog(version = 1, models = listOf(entry(minRamMb = 3072)))
        val vm = viewModel(loadCatalog = { catalog }, deviceRamMb = 8192)

        val state = vm.uiState.value as ModelPickerUiState.Success
        assertEquals(8192L, state.deviceRamMb)
        assertEquals(1, state.models.size)
        assertEquals(ModelCompatibility.WORKS, state.models[0].compatibility)
        assertEquals("m1", state.models[0].entry.id)
    }

    @Test
    fun ramWellAboveThresholdIsWorks() {
        // 3072 * 1.5 = 4608, so 4608 is the exact Works boundary
        assertEquals(
            ModelCompatibility.WORKS,
            ModelPickerViewModel.computeCompatibility(deviceRamMb = 8192, minRamMb = 3072)
        )
        assertEquals(
            ModelCompatibility.WORKS,
            ModelPickerViewModel.computeCompatibility(deviceRamMb = 4608, minRamMb = 3072)
        )
    }

    @Test
    fun ramEqualToMinIsTight() {
        assertEquals(
            ModelCompatibility.TIGHT,
            ModelPickerViewModel.computeCompatibility(deviceRamMb = 3072, minRamMb = 3072)
        )
        // just under the 1.5x headroom boundary is still Tight
        assertEquals(
            ModelCompatibility.TIGHT,
            ModelPickerViewModel.computeCompatibility(deviceRamMb = 4607, minRamMb = 3072)
        )
    }

    @Test
    fun ramBelowMinIsTooBig() {
        assertEquals(
            ModelCompatibility.TOO_BIG,
            ModelPickerViewModel.computeCompatibility(deviceRamMb = 3071, minRamMb = 3072)
        )
        assertEquals(
            ModelCompatibility.TOO_BIG,
            ModelPickerViewModel.computeCompatibility(deviceRamMb = 2048, minRamMb = 3072)
        )
    }

    @Test
    fun mixedCatalogGetsPerEntryBuckets() = runTest(mainDispatcher) {
        val catalog = ModelCatalog(
            version = 1,
            models = listOf(
                entry(id = "small", minRamMb = 2048),  // 4096 >= 3072 -> Works
                entry(id = "medium", minRamMb = 4096), // 4096 == min -> Tight
                entry(id = "large", minRamMb = 6144),  // 4096 < min -> Too big
            ),
        )
        val vm = viewModel(loadCatalog = { catalog }, deviceRamMb = 4096)

        val state = vm.uiState.value as ModelPickerUiState.Success
        val buckets = state.models.associate { it.entry.id to it.compatibility }
        assertEquals(ModelCompatibility.WORKS, buckets["small"])
        assertEquals(ModelCompatibility.TIGHT, buckets["medium"])
        assertEquals(ModelCompatibility.TOO_BIG, buckets["large"])
    }

    @Test
    fun malformedCatalogEmitsError() = runTest(mainDispatcher) {
        val vm = viewModel(
            loadCatalog = { throw ModelCatalogException.Malformed("bad json") },
            deviceRamMb = 8192,
        )

        val state = vm.uiState.value as ModelPickerUiState.Error
        assertTrue(
            "message should explain the catalog is unreadable, got: ${state.message}",
            state.message.contains("could not be read")
        )
    }

    @Test
    fun unexpectedExceptionEmitsGenericErrorWithMessage() = runTest(mainDispatcher) {
        val vm = viewModel(
            loadCatalog = { throw IllegalStateException("boom") },
            deviceRamMb = 8192,
        )

        val state = vm.uiState.value as ModelPickerUiState.Error
        assertTrue(state.message.contains("boom"))
    }

    @Test
    fun retryAfterErrorGoesThroughLoadingToSuccess() = runTest(mainDispatcher) {
        var failing = true
        val catalog = ModelCatalog(version = 1, models = listOf(entry()))
        val vm = viewModel(
            loadCatalog = {
                if (failing) throw ModelCatalogException.Malformed("bad json") else catalog
            },
            deviceRamMb = 8192,
        )
        assertTrue(vm.uiState.value is ModelPickerUiState.Error)

        val states = mutableListOf<ModelPickerUiState>()
        val collector = launch(mainDispatcher) { vm.uiState.toList(states) }

        failing = false
        vm.load()
        collector.cancel()

        assertEquals(3, states.size)
        assertTrue(states[0] is ModelPickerUiState.Error)
        assertTrue(states[1] is ModelPickerUiState.Loading)
        assertTrue(states[2] is ModelPickerUiState.Success)
    }

    @Test
    fun retryAfterErrorCanErrorAgain() = runTest(mainDispatcher) {
        val vm = viewModel(
            loadCatalog = { throw ModelCatalogException.Empty() },
            deviceRamMb = 8192,
        )
        assertTrue(vm.uiState.value is ModelPickerUiState.Error)

        val states = mutableListOf<ModelPickerUiState>()
        val collector = launch(mainDispatcher) { vm.uiState.toList(states) }

        vm.load()
        collector.cancel()

        assertEquals(3, states.size)
        assertTrue(states[1] is ModelPickerUiState.Loading)
        assertTrue(states[2] is ModelPickerUiState.Error)
    }
}
