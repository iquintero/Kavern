package com.iquintero.kavern.ui.reader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iquintero.kavern.domain.reader.EpubReader
import com.iquintero.kavern.domain.repository.ReaderRepository

class EpubReaderViewModel(
    chapterId: Int,
    initialPage: Int,
    readerRepository: ReaderRepository,
    seriesId: Int?,
    volumeId: Int?,
    anonymous: Boolean = false,
) : BaseReaderViewModel(
        chapterId = chapterId,
        initialPage = initialPage,
        reader = EpubReader(readerRepository),
        seriesId = seriesId,
        volumeId = volumeId,
        anonymous = anonymous,
    ) {
    companion object {
        fun provideFactory(
            chapterId: Int,
            initialPage: Int,
            readerRepository: ReaderRepository,
            seriesId: Int?,
            volumeId: Int?,
            anonymous: Boolean = false,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                // The screens ask for BaseReaderViewModel, so that is the class the factory must answer to.
                addInitializer(BaseReaderViewModel::class) {
                    EpubReaderViewModel(
                        chapterId,
                        initialPage,
                        readerRepository,
                        seriesId,
                        volumeId,
                        anonymous,
                    )
                }
            }
    }
}
