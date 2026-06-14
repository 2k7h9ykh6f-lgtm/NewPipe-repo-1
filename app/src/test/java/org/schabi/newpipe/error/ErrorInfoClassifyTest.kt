package org.schabi.newpipe.error

import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.HttpDataSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.player.mediasource.FailedMediaSource
import org.schabi.newpipe.player.resolver.PlaybackResolver
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests for [ErrorInfo.classify], verifying that play-back failures are
 * mapped to the correct [PlaybackErrorSource] category.
 */
class ErrorInfoClassifyTest {

    // -- null / unknown -------------------------------------------------

    @Test
    fun `null throwable maps to PLAYER_INITIALIZATION`() {
        assertEquals(PlaybackErrorSource.PLAYER_INITIALIZATION, ErrorInfo.classify(null))
    }

    @Test
    fun `unrelated RuntimeException maps to PLAYER_INITIALIZATION`() {
        assertEquals(
            PlaybackErrorSource.PLAYER_INITIALIZATION,
            ErrorInfo.classify(RuntimeException("unexpected"))
        )
    }

    // -- Network errors -------------------------------------------------

    @Test
    fun `UnknownHostException maps to NETWORK`() {
        assertEquals(
            PlaybackErrorSource.NETWORK,
            ErrorInfo.classify(UnknownHostException("dns"))
        )
    }

    @Test
    fun `SocketTimeoutException maps to NETWORK`() {
        assertEquals(
            PlaybackErrorSource.NETWORK,
            ErrorInfo.classify(SocketTimeoutException("timed out"))
        )
    }

    @Test
    fun `generic IOException maps to NETWORK`() {
        assertEquals(
            PlaybackErrorSource.NETWORK,
            ErrorInfo.classify(IOException("connection reset"))
        )
    }

    // -- Media parsing errors -------------------------------------------

    @Test
    fun `ExtractionException maps to MEDIA_PARSING`() {
        assertEquals(
            PlaybackErrorSource.MEDIA_PARSING,
            ErrorInfo.classify(ExtractionException("bad page"))
        )
    }

    @Test
    fun `ContentNotAvailableException maps to MEDIA_PARSING`() {
        assertEquals(
            PlaybackErrorSource.MEDIA_PARSING,
            ErrorInfo.classify(ContentNotAvailableException("removed"))
        )
    }

    @Test
    fun `MediaSourceResolutionException maps to MEDIA_PARSING`() {
        assertEquals(
            PlaybackErrorSource.MEDIA_PARSING,
            ErrorInfo.classify(
                FailedMediaSource.MediaSourceResolutionException("no streams")
            )
        )
    }

    @Test
    fun `StreamInfoLoadException maps to MEDIA_PARSING`() {
        assertEquals(
            PlaybackErrorSource.MEDIA_PARSING,
            ErrorInfo.classify(
                FailedMediaSource.StreamInfoLoadException(ExtractionException("parse"))
            )
        )
    }

    @Test
    fun `ResolverException maps to MEDIA_PARSING`() {
        assertEquals(
            PlaybackErrorSource.MEDIA_PARSING,
            ErrorInfo.classify(PlaybackResolver.ResolverException("empty URL"))
        )
    }

    // -- ExoPlaybackException -------------------------------------------

    @Test
    fun `ExoPlaybackException TYPE_RENDERER maps to PLAYER_INITIALIZATION`() {
        val error = ExoPlaybackException.createForRenderer(
            RuntimeException("decoder failed"), "videoRenderer"
        )
        assertEquals(PlaybackErrorSource.PLAYER_INITIALIZATION, ErrorInfo.classify(error))
    }

    @Test
    fun `ExoPlaybackException TYPE_UNEXPECTED maps to PLAYER_INITIALIZATION`() {
        val error = ExoPlaybackException.createForUnexpected(
            RuntimeException("unexpected")
        )
        assertEquals(PlaybackErrorSource.PLAYER_INITIALIZATION, ErrorInfo.classify(error))
    }

    @Test
    fun `ExoPlaybackException TYPE_SOURCE with IOException cause maps to NETWORK`() {
        val error = ExoPlaybackException.createForSource(
            IOException("connection refused"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
        assertEquals(PlaybackErrorSource.NETWORK, ErrorInfo.classify(error))
    }

    @Test
    fun `ExoPlaybackException TYPE_SOURCE with parsing error code maps to MEDIA_PARSING`() {
        val error = ExoPlaybackException.createForSource(
            RuntimeException("bad manifest"),
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
        )
        assertEquals(PlaybackErrorSource.MEDIA_PARSING, ErrorInfo.classify(error))
    }

    @Test
    fun `ExoPlaybackException TYPE_SOURCE with HTTP error cause maps to NETWORK`() {
        val httpError = HttpDataSource.InvalidResponseCodeException(
            403, "Forbidden", null, null, emptyMap(), ByteArray(0)
        )
        val error = ExoPlaybackException.createForSource(
            httpError,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
        assertEquals(PlaybackErrorSource.NETWORK, ErrorInfo.classify(error))
    }

    @Test
    fun `ExoPlaybackException with TIMEOUT error code maps to NETWORK`() {
        val error = ExoPlaybackException.createForSource(
            RuntimeException("timeout"),
            PlaybackException.ERROR_CODE_TIMEOUT
        )
        assertEquals(PlaybackErrorSource.NETWORK, ErrorInfo.classify(error))
    }
}
