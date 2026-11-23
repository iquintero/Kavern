package com.iquintero.kavern.ui.reader.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.iquintero.kavern.domain.repository.ReaderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * The reader screens ask for a `BaseReaderViewModel`, whichever format is open, so each format's factory must answer
 * to that class. A factory registered only under its own class crashes every book the moment it opens.
 *
 * Building the real view model needs Android's main looper, which unit tests do not have, so these tests only check
 * that the factory finds its initializer: any other failure comes after that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelFactoryTest {
    // Creating a view model launches work on the Main dispatcher. A standard test dispatcher only queues that work,
    // so nothing runs and nothing fails later in an unrelated test.
    @Before
    fun setMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private val repository: ReaderRepository =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ReaderRepository::class.java)) { _, _, _ -> null } as ReaderRepository

    private fun hasNoInitializer(factory: ViewModelProvider.Factory): Boolean {
        val failure = runCatching { factory.create(BaseReaderViewModel::class.java, CreationExtras.Empty) }.exceptionOrNull()
        return failure is IllegalArgumentException && failure.message.orEmpty().contains("No initializer")
    }

    @Test
    fun `epub factory answers to the base reader view model`() {
        assertFalse(hasNoInitializer(EpubReaderViewModel.provideFactory(1, 0, repository, null, null)))
    }

    @Test
    fun `image factory answers to the base reader view model`() {
        assertFalse(hasNoInitializer(ImageReaderViewModel.provideFactory(1, 0, repository, null, null)))
    }

    @Test
    fun `pdf factory answers to the base reader view model`() {
        assertFalse(hasNoInitializer(PdfReaderViewModel.provideFactory(1, 0, repository, null, null)))
    }
}
