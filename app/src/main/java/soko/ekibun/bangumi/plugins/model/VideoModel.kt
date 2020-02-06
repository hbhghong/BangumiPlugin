package soko.ekibun.bangumi.plugins.model

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.offline.DownloadHelper
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.util.Util
import soko.ekibun.bangumi.plugins.App
import soko.ekibun.bangumi.plugins.JsEngine
import soko.ekibun.bangumi.plugins.bean.Episode
import soko.ekibun.bangumi.plugins.provider.Provider
import soko.ekibun.bangumi.plugins.provider.video.VideoProvider
import soko.ekibun.bangumi.plugins.subject.LinePresenter
import soko.ekibun.bangumi.plugins.util.HttpUtil
import soko.ekibun.bangumi.plugins.util.NetworkUtil
import java.text.DecimalFormat

class VideoModel(private val linePresenter: LinePresenter, private val onAction: Listener) {

    interface Listener {
        fun onReady(playWhenReady: Boolean)
        fun onBuffering()
        fun onEnded()
        fun onVideoSizeChange(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float)
        fun onError(error: ExoPlaybackException)
    }

    val player: SimpleExoPlayer by lazy {
        val player = SimpleExoPlayer.Builder(linePresenter.activity).build()
        player.addListener(object : Player.EventListener {
            override fun onSeekProcessed() {}
            override fun onPlayerError(error: ExoPlaybackException) {
                onAction.onError(error)
            }

            override fun onLoadingChanged(isLoading: Boolean) {}
            override fun onPositionDiscontinuity(reason: Int) {}
            override fun onRepeatModeChanged(repeatMode: Int) {}
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}
            @SuppressLint("SwitchIntDef")
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> onAction.onEnded()
                    Player.STATE_READY -> onAction.onReady(playWhenReady)
                    Player.STATE_BUFFERING -> onAction.onBuffering()
                }
            }
        })
        player.addVideoListener(object : com.google.android.exoplayer2.video.VideoListener {
            override fun onVideoSizeChanged(
                width: Int,
                height: Int,
                unappliedRotationDegrees: Int,
                pixelWidthHeightRatio: Float
            ) {
                onAction.onVideoSizeChange(width, height, unappliedRotationDegrees, pixelWidthHeightRatio)
            }

            override fun onRenderedFirstFrame() {}
        })
        player
    }

    private var videoInfoCall: HashMap<String, JsEngine.ScriptTask<VideoProvider.VideoInfo>> = HashMap()
    private var videoCall: HashMap<String, JsEngine.ScriptTask<HttpUtil.HttpRequest>> = HashMap()
    //private val videoCacheModel by lazy{ App.getVideoCacheModel(content)}
    fun getVideo(
        key: String, episode: Episode, info: LineInfoModel.LineInfo?,
        onGetVideoInfo: (VideoProvider.VideoInfo?, error: Exception?) -> Unit,
        onGetVideo: (HttpUtil.HttpRequest?, List<StreamKey>?, error: Exception?) -> Unit,
        onCheckNetwork: (() -> Unit) -> Unit
    ) {
        //val videoCache = videoCacheModel.getCache(episode, subject)
        val videoCache = linePresenter.app.videoCacheModel.getVideoCache(episode, linePresenter.subject)
        if (videoCache != null) {
            onGetVideoInfo(VideoProvider.VideoInfo("", videoCache.video.url, videoCache.video.url), null)
            onGetVideo(videoCache.video, videoCache.streamKeys, null)
        } else {
            val provider = linePresenter.app.lineProvider.getProvider(
                Provider.TYPE_VIDEO,
                info?.site ?: ""
            )?.provider as? VideoProvider
            if (info == null || provider == null) {
                if (info?.site == "") {
                    val format =
                        (Regex("""\{\{(.*)\}\}""").find(info.id)?.groupValues ?: listOf("{{ep}}", "ep")).toMutableList()
                    if (format[0] == "{{ep}}") format[1] = "#.##"
                    val url = try {
                        info.id.replace(format[0], DecimalFormat(format[1]).format(episode.sort))
                    } catch (e: Exception) {
                        info.id
                    }
                    onGetVideoInfo(VideoProvider.VideoInfo("", url, url), null)
                    onGetVideo(HttpUtil.HttpRequest(url), null, null)
                } else onGetVideoInfo(null, null)
                return
            }
            val loadFromNetwork: () -> Unit = {
                val jsEngine = linePresenter.app.jsEngine
                videoInfoCall[key]?.cancel(true)
                videoCall[key]?.cancel(true)
                videoInfoCall[key] = provider.getVideoInfo(key, jsEngine, info, episode)
                videoInfoCall[key]?.enqueue({ video ->
                    onGetVideoInfo(video, null)
                    if (video.site == "") {
                        onGetVideo(HttpUtil.HttpRequest(video.url), null, null)
                        return@enqueue
                    }
                    val videoProvider = linePresenter.app.lineProvider.getProvider(
                        Provider.TYPE_VIDEO,
                        video.site
                    )?.provider as VideoProvider
                    videoCall[key] = videoProvider.getVideo(key, jsEngine, video)
                    videoCall[key]?.enqueue({
                        onGetVideo(it, null, null)
                    }, { onGetVideo(null, null, it) })
                }, { onGetVideoInfo(null, it) })
            }
            if (!NetworkUtil.isWifiConnected(linePresenter.activity)) onCheckNetwork(loadFromNetwork) else loadFromNetwork()
        }
    }

    fun createMediaSource(request: HttpUtil.HttpRequest, streamKeys: List<StreamKey>? = null): MediaSource {
        val uri = Uri.parse(request.url)
        val dataSourceFactory = createDataSourceFactory(linePresenter.pluginContext, request, streamKeys != null)
        return when (@C.ContentType Util.inferContentType(uri, request.overrideExtension)) {
            C.TYPE_DASH -> DashMediaSource.Factory(dataSourceFactory)
            C.TYPE_SS -> SsMediaSource.Factory(dataSourceFactory)
            C.TYPE_HLS -> HlsMediaSource.Factory(dataSourceFactory)
            C.TYPE_OTHER -> ProgressiveMediaSource.Factory(dataSourceFactory)
            else -> ProgressiveMediaSource.Factory(dataSourceFactory)
        }.let {
            if (streamKeys != null) it.setStreamKeys(streamKeys)
            it
        }.createMediaSource(uri)
    }

    fun createDownloadRequest(request: HttpUtil.HttpRequest, callback: DownloadHelper.Callback) {
        val uri = Uri.parse(request.url)
        val dataSourceFactory = createDataSourceFactory(linePresenter.pluginContext, request, true)
        val helper = when (@C.ContentType Util.inferContentType(uri, request.overrideExtension)) {
            C.TYPE_DASH -> DownloadHelper.forDash(
                linePresenter.pluginContext,
                uri,
                dataSourceFactory,
                DefaultRenderersFactory(linePresenter.pluginContext)
            )
            C.TYPE_SS -> DownloadHelper.forSmoothStreaming(
                linePresenter.pluginContext,
                uri,
                dataSourceFactory,
                DefaultRenderersFactory(linePresenter.pluginContext)
            )
            C.TYPE_HLS -> DownloadHelper.forHls(
                linePresenter.pluginContext,
                uri,
                dataSourceFactory,
                DefaultRenderersFactory(linePresenter.pluginContext)
            )
            C.TYPE_OTHER -> DownloadHelper.forProgressive(linePresenter.pluginContext, uri)
            else -> DownloadHelper.forProgressive(linePresenter.pluginContext, uri)
        }
        helper.prepare(callback)
    }

    var reload = {}
    fun play(request: HttpUtil.HttpRequest, surface: SurfaceView, streamKeys: List<StreamKey>? = null) {
        reload = {
            player.setVideoSurfaceView(surface)
            player.prepare(createMediaSource(request, streamKeys))
            player.playWhenReady = true
        }
        reload()
    }

    companion object {
        fun createDataSourceFactory(
            context: Context,
            request: HttpUtil.HttpRequest,
            useCache: Boolean = false
        ): DefaultDataSourceFactory {
            val httpSourceFactory = DefaultHttpDataSourceFactory(
                request.header["User-Agent"] ?: "exoplayer",
                null,
                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                true
            )
            request.header.forEach {
                httpSourceFactory.defaultRequestProperties.set(it.key, it.value)
            }
            return DefaultDataSourceFactory(
                context,
                null,
                if (useCache) CacheDataSourceFactory(
                    App.from(context).downloadCache,
                    httpSourceFactory
                ) else httpSourceFactory
            )
        }
    }
}