package org.schabi.newpipe.error

import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.Loader
import java.net.UnknownHostException
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.player.mediasource.FailedMediaSource
import org.schabi.newpipe.player.resolver.PlaybackResolver
import org.schabi.newpipe.util.text.getText

/**
 * An error has occurred in the app. This class contains plain old parcelable data that can be used
 * to report the error and to show it to the user along with correct action buttons.
 */
@Parcelize
class ErrorInfo private constructor(
    val stackTraces: Array<String>,
    val userAction: UserAction,
    val request: String,
    val serviceId: Int?,
    private val message: ErrorMessage,
    /**
     * If `true`, a report button will be shown for this error. Otherwise the error is not something
     * that can really be reported (e.g. a network issue, or content not being available at all).
     */
    val isReportable: Boolean,
    /**
     * If `true`, the process causing this error can be retried, otherwise not.
     */
    val isRetryable: Boolean,
    /**
     * If present, indicates that the exception was a ReCaptchaException, and this is the URL
     * provided by the service that can be used to solve the ReCaptcha challenge.
     */
    val recaptchaUrl: String?,
    /**
     * If present, this resource can alternatively be opened in browser (useful if NewPipe is
     * badly broken).
     */
    val openInBrowserUrl: String?,
    /**
     * If present, categorizes the root cause of a playback failure
     * (media parsing, network, or player initialization). `null` for
     * non-playback errors where the category is not applicable.
     */
    val errorSource: PlaybackErrorSource? = null
) : Parcelable {

    @JvmOverloads
    constructor(
        throwable: Throwable,
        userAction: UserAction,
        request: String,
        serviceId: Int? = null,
        openInBrowserUrl: String? = null,
        errorSource: PlaybackErrorSource? = null
    ) : this(
        throwableToStringList(throwable),
        userAction,
        request,
        serviceId,
        getMessage(throwable, userAction, serviceId),
        isReportable(throwable),
        isRetryable(throwable),
        (throwable as? ReCaptchaException)?.url,
        openInBrowserUrl,
        errorSource
    )

    @JvmOverloads
    constructor(
        throwables: List<Throwable>,
        userAction: UserAction,
        request: String,
        serviceId: Int? = null,
        openInBrowserUrl: String? = null,
        errorSource: PlaybackErrorSource? = null
    ) : this(
        throwableListToStringList(throwables),
        userAction,
        request,
        serviceId,
        getMessage(throwables.firstOrNull(), userAction, serviceId),
        throwables.any(::isReportable),
        throwables.isEmpty() || throwables.any(::isRetryable),
        throwables.firstNotNullOfOrNull { it as? ReCaptchaException }?.url,
        openInBrowserUrl,
        errorSource
    )

    // constructor to manually build ErrorInfo when no throwable is available
    constructor(
        stackTraces: Array<String>,
        userAction: UserAction,
        request: String,
        serviceId: Int?,
        @StringRes message: Int
    ) :
        this(
            stackTraces, userAction, request, serviceId, ErrorMessage(message),
            true, false, null, null
        )

    // constructor with only one throwable to extract service id and openInBrowserUrl from an Info
    constructor(
        throwable: Throwable,
        userAction: UserAction,
        request: String,
        info: Info?
    ) :
        this(throwable, userAction, request, info?.serviceId, info?.url)

    // constructor with multiple throwables to extract service id and openInBrowserUrl from an Info
    constructor(
        throwables: List<Throwable>,
        userAction: UserAction,
        request: String,
        info: Info?
    ) :
        this(throwables, userAction, request, info?.serviceId, info?.url)

    fun getServiceName(): String {
        return getServiceName(serviceId)
    }

    fun getMessage(context: Context): CharSequence {
        return message.getText(context)
    }

    /**
     * Returns the human-readable label for [errorSource], or `null` if no
     * error source was set (i.e. for non-playback errors).
     */
    fun getSourceLabel(context: Context): String? {
        return errorSource?.let { context.getString(it.labelRes) }
    }

    companion object {
        @Parcelize
        class ErrorMessage(
            @StringRes
            private val stringRes: Int,
            private vararg val formatArgs: String
        ) : Parcelable {
            fun getText(context: Context): CharSequence {
                // Ensure locale aware context via ContextCompat.getContextForLanguage() (just in case context is not AppCompatActivity)
                val ctx = ContextCompat.getContextForLanguage(context)
                return if (formatArgs.isEmpty()) {
                    ctx.getText(stringRes)
                } else {
                    // ContextCompat.getString() with formatArgs does not exist, so we just
                    // replicate its source code but with formatArgs
                    ctx.resources.getText(stringRes, *formatArgs)
                }
            }
        }

        const val SERVICE_NONE = "<unknown_service>"

        const val YOUTUBE_IP_BAN_FAQ_URL = "https://newpipe.net/FAQ/#ip-banned-youtube"

        private fun getServiceName(serviceId: Int?) = // not using getNameOfServiceById since we want to accept a nullable serviceId and we
            // want to default to SERVICE_NONE
            ServiceList.all().firstOrNull { it.serviceId == serviceId }?.serviceInfo?.name
                ?: SERVICE_NONE

        fun throwableToStringList(throwable: Throwable) = arrayOf(throwable.stackTraceToString())

        fun throwableListToStringList(throwableList: List<Throwable>) = throwableList.map { it.stackTraceToString() }.toTypedArray()

        fun getMessage(
            throwable: Throwable?,
            action: UserAction?,
            serviceId: Int?
        ): ErrorMessage {
            return when {
                // player exceptions
                // some may be IOException, so do these checks before isNetworkRelated!
                throwable is ExoPlaybackException -> {
                    val cause = throwable.cause
                    when {
                        cause is HttpDataSource.InvalidResponseCodeException -> {
                            if (cause.responseCode == 403) {
                                if (serviceId == YouTube.serviceId) {
                                    ErrorMessage(R.string.youtube_player_http_403)
                                } else {
                                    ErrorMessage(R.string.player_http_403)
                                }
                            } else {
                                ErrorMessage(R.string.player_http_invalid_status, cause.responseCode.toString())
                            }
                        }

                        cause is Loader.UnexpectedLoaderException && cause.cause is ExtractionException ->
                            getMessage(throwable, action, serviceId)

                        throwable.type == ExoPlaybackException.TYPE_SOURCE ->
                            ErrorMessage(R.string.player_stream_failure)

                        throwable.type == ExoPlaybackException.TYPE_UNEXPECTED ->
                            ErrorMessage(R.string.player_recoverable_failure)

                        else ->
                            ErrorMessage(R.string.player_unrecoverable_failure)
                    }
                }

                throwable is FailedMediaSource.FailedMediaSourceException ->
                    getMessage(throwable.cause, action, serviceId)

                throwable is PlaybackResolver.ResolverException ->
                    ErrorMessage(R.string.player_stream_failure)

                // content not available exceptions
                throwable is AccountTerminatedException ->
                    throwable.message
                        ?.takeIf { reason -> !reason.isEmpty() }
                        ?.let { reason ->
                            ErrorMessage(
                                R.string.account_terminated_service_provides_reason,
                                getServiceName(serviceId),
                                reason
                            )
                        }
                        ?: ErrorMessage(R.string.account_terminated)

                throwable is AgeRestrictedContentException ->
                    ErrorMessage(R.string.restricted_video_no_stream)

                throwable is GeographicRestrictionException ->
                    ErrorMessage(R.string.georestricted_content)

                throwable is PaidContentException ->
                    ErrorMessage(R.string.paid_content)

                throwable is PrivateContentException ->
                    ErrorMessage(R.string.private_content)

                throwable is SoundCloudGoPlusContentException ->
                    ErrorMessage(R.string.soundcloud_go_plus_content)

                throwable is UnsupportedContentInCountryException ->
                    ErrorMessage(R.string.unsupported_content_in_country)

                throwable is YoutubeMusicPremiumContentException ->
                    ErrorMessage(R.string.youtube_music_premium_content)

                throwable is SignInConfirmNotBotException ->
                    ErrorMessage(
                        R.string.sign_in_confirm_not_bot_error,
                        getServiceName(serviceId),
                        YOUTUBE_IP_BAN_FAQ_URL
                    )

                throwable is ContentNotAvailableException ->
                    ErrorMessage(R.string.content_not_available)

                // other extractor exceptions
                throwable is ContentNotSupportedException ->
                    ErrorMessage(R.string.content_not_supported)

                // ReCaptchas will be handled in a special way anyway
                throwable is ReCaptchaException ->
                    ErrorMessage(R.string.recaptcha_request_toast)

                // test this at the end as many exceptions could be a subclass of IOException
                throwable != null && throwable.isNetworkRelated ->
                    ErrorMessage(R.string.network_error)

                // an extraction exception unrelated to the network
                // is likely an issue with parsing the website
                throwable is ExtractionException ->
                    ErrorMessage(R.string.parsing_error)

                // user actions (in case the exception is null or unrecognizable)
                action == UserAction.UI_ERROR ->
                    ErrorMessage(R.string.app_ui_crash)

                action == UserAction.REQUESTED_COMMENTS ->
                    ErrorMessage(R.string.error_unable_to_load_comments)

                action == UserAction.SUBSCRIPTION_CHANGE ->
                    ErrorMessage(R.string.subscription_change_failed)

                action == UserAction.SUBSCRIPTION_UPDATE ->
                    ErrorMessage(R.string.subscription_update_failed)

                action == UserAction.LOAD_IMAGE ->
                    ErrorMessage(R.string.could_not_load_thumbnails)

                action == UserAction.DOWNLOAD_OPEN_DIALOG ->
                    ErrorMessage(R.string.could_not_setup_download_menu)

                else ->
                    ErrorMessage(R.string.error_snackbar_message)
            }
        }

        fun isReportable(throwable: Throwable?): Boolean {
            return when (throwable) {
                // we don't have an exception, so this is a manually built error, which likely
                // indicates that it's important and is thus reportable
                null -> true

                // if the service explicitly said that content is not available (e.g. age
                // restrictions, video deleted, etc.), there is no use in letting users report it
                is ContentNotAvailableException -> !isContentSurelyNotAvailable(throwable)

                // we know the content is not supported, no need to let the user report it
                is ContentNotSupportedException -> false

                // happens often when there is no internet connection; we don't use
                // `throwable.isNetworkRelated` since any `IOException` would make that function
                // return true, but not all `IOException`s are network related
                is UnknownHostException -> false

                // by default, this is an unexpected exception, which the user could report
                else -> true
            }
        }

        fun isRetryable(throwable: Throwable?): Boolean {
            return when (throwable) {
                // if we know the content is surely not available, retrying won't help
                is ContentNotAvailableException -> !isContentSurelyNotAvailable(throwable)

                // we know the content is not supported, retrying won't help
                is ContentNotSupportedException -> false

                // by default (including if throwable is null), enable retrying (though the retry
                // button will be shown only if a way to perform the retry is implemented)
                else -> true
            }
        }

        /**
         * Unfortunately sometimes [ContentNotAvailableException] may not indicate that the content
         * is blocked/deleted/paid, but may just indicate that we could not extract it. This is an
         * inconsistency in the exceptions thrown by the extractor, but until it is fixed, this
         * function will distinguish between the two types.
         * @return `true` if the content is not available because of a limitation imposed by the
         * service or the owner, `false` if the extractor could not extract info about it
         */
        fun isContentSurelyNotAvailable(e: ContentNotAvailableException): Boolean {
            return when (e) {
                is AccountTerminatedException,
                is AgeRestrictedContentException,
                is GeographicRestrictionException,
                is PaidContentException,
                is PrivateContentException,
                is SoundCloudGoPlusContentException,
                is UnsupportedContentInCountryException,
                is YoutubeMusicPremiumContentException -> true

                else -> false
            }
        }

        /**
         * Classifies a throwable into a [PlaybackErrorSource] category so that
         * the user-facing error message can indicate the root cause of a
         * playback failure (media parsing, network, or player initialization).
         *
         * This function intentionally does not depend on any extractor types
         * beyond [ExtractionException], which is already used by [getMessage].
         *
         * @param throwable the exception that caused the playback failure
         * @return the categorised error source
         */
        @JvmStatic
        fun classify(throwable: Throwable?): PlaybackErrorSource {
            if (throwable == null) {
                return PlaybackErrorSource.PLAYER_INITIALIZATION
            }

            // ExoPlayer playback exceptions — use type and error code
            if (throwable is ExoPlaybackException) {
                return when {
                    throwable.type == ExoPlaybackException.TYPE_RENDERER ->
                        PlaybackErrorSource.PLAYER_INITIALIZATION

                    throwable.type == ExoPlaybackException.TYPE_UNEXPECTED ->
                        PlaybackErrorSource.PLAYER_INITIALIZATION

                    throwable.cause is HttpDataSource.InvalidResponseCodeException ->
                        PlaybackErrorSource.NETWORK

                    throwable.type == ExoPlaybackException.TYPE_SOURCE
                            && throwable.cause != null
                            && throwable.cause!!.isNetworkRelated ->
                        PlaybackErrorSource.NETWORK

                    throwable.type == ExoPlaybackException.TYPE_SOURCE ->
                        PlaybackErrorSource.MEDIA_PARSING

                    // Fall back to error-code based classification for any
                    // ExoPlaybackException not covered above.
                    else -> classifyByErrorCode(throwable.errorCode)
                }
            }

            // FailedMediaSource wrappers — unwrap and classify the cause
            if (throwable is FailedMediaSource.MediaSourceResolutionException
                || throwable is FailedMediaSource.StreamInfoLoadException) {
                return PlaybackErrorSource.MEDIA_PARSING
            }
            if (throwable is FailedMediaSource.FailedMediaSourceException) {
                return classify(throwable.cause)
            }

            // Resolver failure — the stream could not be turned into a MediaSource
            if (throwable is PlaybackResolver.ResolverException) {
                return PlaybackErrorSource.MEDIA_PARSING
            }

            // Generic network check (covers IOException subtypes)
            if (throwable.isNetworkRelated) {
                return PlaybackErrorSource.NETWORK
            }

            // Non-network extraction errors are parsing failures
            if (throwable is ExtractionException) {
                return PlaybackErrorSource.MEDIA_PARSING
            }

            return PlaybackErrorSource.PLAYER_INITIALIZATION
        }

        /**
         * Maps ExoPlayer [PlaybackException] error codes to an error source.
         */
        private fun classifyByErrorCode(errorCode: Int): PlaybackErrorSource {
            return when (errorCode) {
                // Network / timeout errors
                PlaybackException.ERROR_CODE_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_UNSPECIFIED ->
                    PlaybackErrorSource.NETWORK

                // Parsing / source format errors
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                    PlaybackErrorSource.MEDIA_PARSING

                // Everything else (decoder, renderer, remote, API) is a
                // player-internal failure.
                else -> PlaybackErrorSource.PLAYER_INITIALIZATION
            }
        }
    }
}
