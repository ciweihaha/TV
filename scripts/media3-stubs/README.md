# Media3 私有 API 兼容垫片（FongMi/TV 5.8.7）

## 背景

上游 FongMi/TV 5.8.7 的播放器代码用到了 FongMi 私有 Media3 扩展里的大量类/方法，
而公开的 FongMi/media 源码（`release-1.11.0-fongmi`，3c2cbe8a；libass 用 9dae39e9）
根本不包含它们——这些类只存在于 FongMi 的官方 APK 里。直接后果：

- `:app:dataBindingGenBaseClassesLeanbackDebug` 的 `exo_duration` 报错修好之后，
  `:app:compileLeanbackDebugJavaWithJavac` 报 300 个错误（javac 默认最多报 100 个，
  实际缺失更多），涉及 45 个类 + 若干个真实类上缺失的方法。

## 做法

本目录提供这 45 个类的最小兼容实现（API 按 TV 代码的实际用法反推），以及对 15 个
真实 Media3 文件的补丁（加缺失的扩展方法）。CI 里 `install.py` 在构建 AAR 之前：

1. 把 `stubs/media3/` 的文件拷进 `media3/` checkout（按模块目录一一对应），
   把 `stubs/media3-ass/` 的拷进 `media3-ass/` checkout；另外 4 个垫片
   （`SubtitleSelectionState`、`SecondaryTextOutput`、`SubtitleTranscript`、
   `SecondaryTextTrackSelector`）同时拷进 `media3-ass/`——libass checkout 是独立
   构建的，而 `LibassSubtitleController` 垫片与打过补丁的 `LibassPlaybackSession`
   引用了它们（第 100 轮修复，否则 libass AAR 编译报 cannot find symbol）；
2. 给 `Player`、`PlayerView`、`MpvPlayer`、`MpvCommandDispatcher`、
   `MpvPlaybackProperties`、`MpvPlayerConfig`、`MpvSubtitleOptions`、
   `MpvLibrary`、`MpvAudioFilter`、`MatroskaExtractor`、`PlayerSeekView`、
   `SubtitleView`、`PreloadManagerListener`、`LibassPlaybackSession`、
   `DefaultExtractorsFactory` 打补丁。

另外 4 个类因为模块依赖关系放不进 Media3 checkout，改放在 app 源码里
（app 本来就依赖全部 AAR，不存在依赖问题）：

- `app/src/main/java/androidx/media3/exoplayer/text/DefaultSubtitleContentLoader.java`
- `app/src/main/java/androidx/media3/exoplayer/text/SubtitleTranscriptSession.java`
- `app/src/main/java/androidx/media3/ui/danmaku/DanmakuPlayerViewController.java`
- `app/src/main/java/androidx/media3/ui/libass/LibassPlayerViewController.java`

## 垫片清单（45 个类）

| 位置 | 类 | 运行时行为 |
|---|---|---|
| common | `DecoderMode`（enum） | 真实枚举，AUTO/HARDWARE/SOFTWARE/FFMPEG |
| common | `DiscMenuController` / `DiscMenuState` | 接口+状态快照；返回 EMPTY/不可用 |
| common | `VideoTransform` | 真实值对象；含 `DEFAULT`（单位变换）；PlayerView 补丁会实际缩放 surface |
| common/audio | `AudioChannelMode` / `AudioChannelMix` | 真实矩阵数学，供 mpv pan 滤镜用 |
| common/audio | `AudioEffectParameters` | 真实值对象 |
| common/audio | `AudioEffectProcessor` | 不激活，音频直通 |
| common/text | `SubtitleContent` / `SubtitleOffsets` / `SubtitleSelectionState` | 真实值对象；字幕内容第 114 轮起由 MpvPlayer 填充（外挂文件），选择状态按当前 Tracks 计算 |
| common/util | `LocalClearKeyLicense` | 真实解析 ClearKey JSON |
| common/util | `TrackSelectionUtil` | 真实实现（基于 stock Player API 选轨/循环） |
| datasource | `IsoUri` / `ProgressiveIsoCache` | fragment 编码 edition；缓存直通 |
| decoder_ffmpeg | `FfmpegMotionRenderersFactory` | stock 渲染器；无运动插值/解码器切换 |
| effect | `ColorToneAdjustEffect` / `DetailAdjustEffect` / `VideoAdjustment` | 真实实现（第 119 轮）：色调走 `ToneAdjustShaderProgram`（亮度/对比度/饱和度/伽马/色相旋转/色温增益 GLSL），细节走 `DetailAdjustShaderProgram`（3x3 反锐化掩模 + 阈值门 + 暗部提升）；`VideoAdjustment` 真实值对象；HDR 时直通 |
| exoplayer | `DecoderMode` / `DecoderManager` | 记住选择；不重建渲染器 |
| exoplayer/audio | `AudioEqualizerController` | 真实实现（第 116 轮）：平台 `Equalizer` 挂到播放器音频 session，按最近中心频率映射频段，电平钳制到平台范围；换 session 重建；无 session 时暂缓、创建失败报 false |
| exoplayer_hls | `CmgConfiguration` | 恒返回 null（不识别 CMG 流） |
| exoplayer/iso | `BdjRuntime` / `IsoDiscPreparation` / `IsoNavigationPlayerController` | BDJ 不可用；ISO 按后缀识别；ISO 当普通文件播，无菜单 |
| exoplayer/source | `ContentTypeResolvingMediaSource` | 委托包装 |
| exoplayer/source | `DefaultPositionMediaSource` / `EndTrimMediaSource` | 真实实现（第 113 轮）：`ForwardingTimeline` 改窗口默认位置 / 截断窗口与 period 时长；直播、未知时长、多 period 不截断 |
| exoplayer/text | `SecondaryTextOutput` / `SubtitleTranscript` / `SubtitleTranscriptParserFactory` | 无转录字幕（转录即播轨道仍未激活） |
| exoplayer/trackselection | `SecondaryTextTrackSelector` | 委托给解码器选择器 |
| exoplayer_libass | `LibassConfiguration` / `LibassFontConfig` / `LibassFontFile` / `LibassSubtitleController` | 配置真实携带；fonts.conf 真实生成；字体族名纯 Java 解析；控制器报无副字幕 |
| mpvplayer | `MpvConfigFileInspector` / `MpvDecoderMode` / `MpvSubtitleContentLoader` / `media/MpvDrmSupport` | mpv.conf 真实解析；DRM 一律判 MPV 不支持（走 Exo） |
| app 内 | 见上 | 字幕内容：第 114 轮起 `DefaultSubtitleContentLoader` 真实下载并解析（SSA/ASS、WebVTT、SubRip、TX3G、TTML、图片字幕）；第 115 轮起 `SubtitleTranscriptSession.getContent` 真实实现（Exo 引擎对话框：按轨道 id / 语言+标签解析外挂字幕文件，后台加载、按轨道缓存、失败可重试）；第 121 轮起 getContent 按字幕延时平移条目时间（缓存记下每条目集的偏移，延时改了重载）；第 122 轮起 MPV 对话框内容返回时同样按延时平移；转录即播轨道仍未激活；danmaku/libass UI 不激活 |

## 补丁清单（16 个文件）

- `libraries/common/.../Player.java`：默认方法 `getDiscMenuState()`（→EMPTY）、
  `getDecoderMode(int)`（→null）、`getSubtitleOffsets()`（→ZERO）、
  `setSubtitleOffsets(...)`（no-op）；`Player.Listener.onDiscMenuStateChanged(...)`（no-op）；
  常量 `EVENT_DISC_MENU_STATE_CHANGED = 33`（第 101 轮补，永不触发）。
- `libraries/exoplayer/.../ExoPlayerImpl.java`（第 121 轮）：重写
  `get/setSubtitleOffsets`——存取主/副字幕延时，主延时经 stock 的
  `setTextOffsetMs` 实时作用于屏幕字幕（`TextRenderer` 按偏移平移 cue 时间，
  所有文本渲染器共用同一个偏移，故副轨屏幕上也跟主延时；副延时目前只用于对话框）；
- `libraries/ui/.../PlayerView.java`：`setDiscMenuController/getDiscMenuController`、
  `getVideoTransform/setVideoTransform`（真实缩放 surface）、
  `getVideoAspectRatioOverride/setVideoAspectRatioOverride`（第 120 轮起真实应用：override > 0 时 content frame 用 override 比例，否则用视频自然比例）、
  `setBackendPlayer(...)`（转调 `setPlayer`）、`dispatchDiscMenuTouchEvent(...)`（false）。
  第 103 轮再加：`animateVideoTransform(...)`（ViewPropertyAnimator 动画到目标变换）、
  `finishVideoTransformAnimation()`（取消动画）、`getVideoBounds()`（surface 视图边界）。
  第 110 轮修：pan 分量是分数（像素偏移 ÷ 内容尺寸 × 缩放），`setVideoTransform` /
  `animateVideoTransform` 须换算成像素再设 translation（第 103 轮直接传分数，
  平移基本不动）；取消动画后对齐到已存变换，避免下次双指缩放起算状态错乱。
- `libraries/mpvplayer/.../MpvPlayer.java`：实现 `DiscMenuController`（全 false），
  加 `get/setVideoDecoderMode`、`getSubtitleContentForSelection`（第 114 轮起真实现：
  按 mpv track id 读 `track-list` 的 `external-filename`，回退到当前 MediaItem 字幕配置
  按语言+标签匹配，后台线程经 `MpvSubtitleContentLoader` 加载并缓存，LOADING/COMPLETE/ERROR；
  第 122 轮起返回时按该轨的字幕延时平移条目时间，缓存仍存文件时间）、
  `setSubtitleContentEnabled`、`canRetry/retrySubtitleContent`（第 114 轮起真实现）、
  `get/setSubtitleOffsets`（第 122 轮起 set 经 stock 的 `setTextOffsetMs` 驱动 mpv
  `sub-delay`；副延时只用于对话框）、`getSubtitleSelectionState`（第 114 轮起按当前
  Tracks 计算主/副字幕选择）、
  `setSubtitleContentLoader`、`setSecondaryTextTrackSelectionOverride/AutoSelectionEnabled`；
  `Builder.setVideoDecoderMode(...)`。第 101 轮再加：`setVerboseLoggingEnabled`、
  `setDiscDataSourceFactory`（仅保存）、`resetSecondaryTextTrackSelection`
  （第 114 轮起真清掉已存副轨覆盖）、
  `setLogListener`/`setAudioFilterListener`（永不触发）、运动插值
  `setMotionInterpolationFps`（存值，返回已完成 Future）/
  `getMotionInterpolationFps`/`isMotionInterpolationSupported`（false）/
  `isMotionInterpolationUpdatePending`（false）/
  `getMotionInterpolationEffectiveTargetFps`/`add/removeMotionInterpolationListener`。
  第 112 轮把脚本会话接成真实现：`setStartupScripts`（mpv 初始化前排队，
  首次 prepare 时在播片前用 `load-script` 加载）/`loadScript`（`load-script`
  命令，回调报接纳结果，clientId 是本地 token）/`getScriptData`（读 mpv
  `user-data/<key>` 属性，Lua runner 用 `mp.set_property_native` 发布状态）/
  `getInputBindings`（读 mpv `input-bindings` 属性）/`runScriptBinding`
  （`script-binding` 命令）/`unloadScript`（仅忘掉 token——mpv 没有
  unload-script 命令，旧脚本靠 TV 的 generation 检查忽略）/`setLogListener`
  （注册 MPVLib 日志观察者，按严重度转发）。
- `libraries/mpvplayer/.../nativebridge/MpvCommandDispatcher.java`（第 112 轮）：
  `loadScript(...)`（`load-script` 命令）、`runScriptBinding(...)`
  （`script-binding` 命令）。
- `libraries/mpvplayer/.../nativebridge/MpvPlaybackProperties.java`（第 112 轮）：
  `getPropertyString(...)`（转调 `MpvPropertyAccessor.getString`，mpv 未初始化时返回 null）。
- `libraries/mpvplayer/.../MpvPlayerConfig.java`（第 101 轮）：
  `Builder.setDataSourceFactory(...)`、`setMotionInterpolationFps(...)`（仅保存，
  播放器不消费）+ 对应 getter；第 102 轮补 `Builder.setDefaultFontDirectories(File, File)`
  （仅保存，TV 传两次同一外部字体目录）。第 109 轮：`addAndroidSubtitleOptions`
  在字幕选项无字体目录时回退到默认字体目录发 `sub-fonts-dir`（无字体覆盖时
  mpv 也能找到用户字体）。
- `libraries/mpvplayer/.../MpvSubtitleOptions.java`（第 101 轮）：
  `Builder.setFontsDirectory/setVerticalOffset/setBitmapSubtitleBrightness/
  setSecondarySubtitlePosition/setSecondaryVerticalOffset/setSecondaryAssStyleOverride/
  setFontFamily/setDefaultStyle`（仅保存，不生效）+ 对应 getter。
  第 108 轮：`MpvSubtitleOptionDefaults.addSubtitleOptions` 补丁把其中 5 个接到
  mpv 选项——`sub-fonts-dir` / `sub-font` / `sub-offset-y` /
  `secondary-sub-offset-y` / `image-subs-brightness`（选项名取自 TV 的
  MpvUtil managed 列表）。
- `libraries/mpvplayer/.../MpvLibrary.java`（第 101 轮）：
  `setLibraryLoader(BooleanSupplier)`（仅保存；TV 代码传 `MpvUtil::loadRuntime`，
  `() -> boolean` 方法引用只能是函数式接口，不能是 `LibraryLoader` 类）。
- `libraries/mpvplayer/.../MpvAudioFilter.java`（第 101 轮）：
  `Builder.addDynamicNormalization(...)`（近似映射为 ffmpeg `dynaudnorm` 滤镜）。
  第 111 轮修正映射：第 101 轮把 maxGain 写进 `p`（目标峰值，范围 0–1），
  `p=4.0` 超范围会被 ffmpeg 拒绝；正确是 `m`（最大增益因子，1–100）。
- `libraries/extractor/.../MatroskaExtractor.java`（第 101 轮）：
  常量 `FLAG_DEFER_SEEK_FOR_CUES = 1 << 2`（定义后被忽略）。
- `libraries/ui/.../PlayerSeekView.java`（第 101 轮）：
  `setDurationSupplier(LongSupplier)`（真实生效：覆盖时间线时长）。
- `libraries/ui/.../SubtitleView.java`（第 101 轮）：
  `setBitmapSubtitleBrightness(...)`（接受但不应用）。
- `libraries/exoplayer/.../PreloadManagerListener.java`（第 101 轮）：
  默认方法 `onPreloadPaused(...)`（永不触发）。
- `libraries/exoplayer_libass/.../LibassPlaybackSession.java`：
  `LibassConfiguration` 构造器、`createMediaComponents(..., SubtitleTranscript)` 重载、
  `setBottomPositionFraction` / `setSecondaryBottomPositionFraction` / `setFontScale`（no-op）。
- `libraries/extractor/.../DefaultExtractorsFactory.java`：
  `setMatroskaExtractorFlagsForHttpSources(...)`（转调通用方法）、
  `setMp4ClearKeys(...)`（接受但不应用）。

## 已知降级（个人使用可接受，后续轮次可逐步恢复）

- 光盘菜单 / BD-J：不可用（ISO 当普通文件播）。
- 解码器模式切换（AUTO/HARDWARE/SOFTWARE/FFMPEG）：UI 可选但不生效。
- 双字幕（副字幕轨）：第 114 轮起 `getSubtitleSelectionState` 按当前 Tracks 计算主/副选择；
  字幕转录（直播字幕流）仍不工作。
- 字幕内容列表：第 114 轮起工作（外挂字幕文件：下载+解析进对话框；内嵌字幕无文件可取，
  仍报 UNSUPPORTED）。
- 视频效果（色彩/细节调整）：不生效（直通）。
- 音频均衡器/声道混合：Exo 侧不生效；MPV 侧混音矩阵真实生效。
- ClearKey MP4 无 DRM 会话解密：不工作（走标准 DRM 路径的仍正常）。
- 截尾（跳过片尾）、预加载定位：第 113 轮起真实实现（`ForwardingTimeline` 截断
  窗口/period 时长、改窗口默认位置）；仅时间线裁剪、未包 `ClippingMediaPeriod`
  （播到截断点即按 period 结束处理），直播/未知时长/多 period 源不截断。
- CMG 流识别：不识别（按用户设置的引擎播放）。
- 启动脚本（mpv 脚本会话）：第 112 轮起真实接入 mpv（`load-script` /
  `script-binding` / `user-data/...` 状态属性 / 日志观察者）；`unloadScript`
  仍只是忘掉 token（mpv 没有 unload 命令），旧脚本实例靠 TV 的 generation
  检查忽略；`loadScript` 的回调只报命令接纳结果，脚本自身的加载状态由 TV
  轮询 `getScriptData` 确认。
- 运动插值：不生效（帧率可设可读，但画面不插值）。
- MPV 音频滤镜监听：永不触发（`setAudioFilterListener` 仍是空实现）。
- 字幕字体目录、垂直偏移、位图字幕亮度、字体族：第 108 轮起经 `MpvSubtitleOptionDefaults`
  补丁接入 mpv 选项（`sub-fonts-dir` / `sub-font` / `sub-offset-y` /
  `secondary-sub-offset-y` / `image-subs-brightness`），不再是存值丢弃。
- 动态响度归一化：映射为 ffmpeg `dynaudnorm`（`f`=帧长毫秒，`m`=最大增益因子；
  第 111 轮已按 ffmpeg 文档核对，第 101 轮误把增益写进 `p` 已修正）。
- Matroska 延迟 cue 查找标志：定义后被提取器忽略。

## 维护注意

- 上游 FongMi/media 一旦公开这些类（或 TV 代码升级），本目录应删除，
  `install.py` 的补丁锚点会因源码变化而直接失败（不会静默跳过）。
- `install.py` 每个补丁都用精确锚点 + 数量断言；锚点找不到就报错退出。
- 下游 fork `15840213978a/fongmi-android6-main` 的 `restore_official_*.py` 只还原
  UDF/ISO 导航/Dolby Vision 等少数类，不覆盖本轮的 45 个类，故未采用。
