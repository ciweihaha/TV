# Media3 私有 API 兼容垫片（FongMi/TV 5.8.7）

## 背景

上游 FongMi/TV 5.8.7 的播放器代码用到了 FongMi 私有 Media3 扩展里的大量类/方法，
而公开的 FongMi/media 源码（`release-1.11.0-fongmi`，3c2cbe8a；libass 用 9dae39e9）
根本不包含它们——这些类只存在于 FongMi 的官方 APK 里。直接后果：

- `:app:dataBindingGenBaseClassesLeanbackDebug` 的 `exo_duration` 报错修好之后，
  `:app:compileLeanbackDebugJavaWithJavac` 报 300 个错误（javac 默认最多报 100 个，
  实际缺失更多），涉及 45 个类 + 若干个真实类上缺失的方法。

## 做法

本目录提供这 45 个类的最小兼容实现（API 按 TV 代码的实际用法反推），以及对 13 个
真实 Media3 文件的补丁（加缺失的扩展方法）。CI 里 `install.py` 在构建 AAR 之前：

1. 把 `stubs/media3/` 的文件拷进 `media3/` checkout（按模块目录一一对应），
   把 `stubs/media3-ass/` 的拷进 `media3-ass/` checkout；另外 4 个垫片
   （`SubtitleSelectionState`、`SecondaryTextOutput`、`SubtitleTranscript`、
   `SecondaryTextTrackSelector`）同时拷进 `media3-ass/`——libass checkout 是独立
   构建的，而 `LibassSubtitleController` 垫片与打过补丁的 `LibassPlaybackSession`
   引用了它们（第 100 轮修复，否则 libass AAR 编译报 cannot find symbol）；
2. 给 `Player`、`PlayerView`、`MpvPlayer`、`MpvPlayerConfig`、`MpvSubtitleOptions`、
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
| common | `VideoTransform` | 真实值对象；PlayerView 补丁会实际缩放 surface |
| common/audio | `AudioChannelMode` / `AudioChannelMix` | 真实矩阵数学，供 mpv pan 滤镜用 |
| common/audio | `AudioEffectParameters` | 真实值对象 |
| common/audio | `AudioEffectProcessor` | 不激活，音频直通 |
| common/text | `SubtitleContent` / `SubtitleOffsets` / `SubtitleSelectionState` | 真实值对象；内容一律 UNSUPPORTED/EMPTY（无副轨） |
| common/util | `LocalClearKeyLicense` | 真实解析 ClearKey JSON |
| common/util | `TrackSelectionUtil` | 真实实现（基于 stock Player API 选轨/循环） |
| datasource | `IsoUri` / `ProgressiveIsoCache` | fragment 编码 edition；缓存直通 |
| decoder_ffmpeg | `FfmpegMotionRenderersFactory` | stock 渲染器；无运动插值/解码器切换 |
| effect | `ColorToneAdjustEffect` / `DetailAdjustEffect` / `VideoAdjustment` | 真实值对象；效果为直通 shader（不改变画面） |
| exoplayer | `DecoderMode` / `DecoderManager` | 记住选择；不重建渲染器 |
| exoplayer/audio | `AudioEqualizerController` | 恒返回成功，不调均衡器 |
| exoplayer_hls | `CmgConfiguration` | 恒返回 null（不识别 CMG 流） |
| exoplayer/iso | `BdjRuntime` / `IsoDiscPreparation` / `IsoNavigationPlayerController` | BDJ 不可用；ISO 按后缀识别；ISO 当普通文件播，无菜单 |
| exoplayer/source | `ContentTypeResolvingMediaSource` / `DefaultPositionMediaSource` / `EndTrimMediaSource` | 委托包装；不截尾、不定位 |
| exoplayer/text | `SecondaryTextOutput` / `SubtitleTranscript` / `SubtitleTranscriptParserFactory` | 无转录字幕 |
| exoplayer/trackselection | `SecondaryTextTrackSelector` | 委托给解码器选择器 |
| exoplayer_libass | `LibassConfiguration` / `LibassFontConfig` / `LibassFontFile` / `LibassSubtitleController` | 配置真实携带；fonts.conf 真实生成；字体族名纯 Java 解析；控制器报无副字幕 |
| mpvplayer | `MpvConfigFileInspector` / `MpvDecoderMode` / `MpvSubtitleContentLoader` / `media/MpvDrmSupport` | mpv.conf 真实解析；DRM 一律判 MPV 不支持（走 Exo） |
| app 内 | 见上 | 字幕内容 UNSUPPORTED；无转录；danmaku/libass UI 不激活 |

## 补丁清单（13 个文件）

- `libraries/common/.../Player.java`：默认方法 `getDiscMenuState()`（→EMPTY）、
  `getDecoderMode(int)`（→null）、`getSubtitleOffsets()`（→ZERO）、
  `setSubtitleOffsets(...)`（no-op）；`Player.Listener.onDiscMenuStateChanged(...)`（no-op）；
  常量 `EVENT_DISC_MENU_STATE_CHANGED = 33`（第 101 轮补，永不触发）。
- `libraries/ui/.../PlayerView.java`：`setDiscMenuController/getDiscMenuController`、
  `getVideoTransform/setVideoTransform`（真实缩放 surface）、
  `getVideoAspectRatioOverride/setVideoAspectRatioOverride`（仅保存）、
  `setBackendPlayer(...)`（转调 `setPlayer`）、`dispatchDiscMenuTouchEvent(...)`（false）。
- `libraries/mpvplayer/.../MpvPlayer.java`：实现 `DiscMenuController`（全 false），
  加 `get/setVideoDecoderMode`、`getSubtitleContentForSelection`（UNSUPPORTED）、
  `setSubtitleContentEnabled`、`canRetry/retrySubtitleContent`（false/no-op）、
  `get/setSubtitleOffsets`（保存/返回）、`getSubtitleSelectionState`（EMPTY）、
  `setSubtitleContentLoader`、`setSecondaryTextTrackSelectionOverride/AutoSelectionEnabled`；
  `Builder.setVideoDecoderMode(...)`。第 101 轮再加：`setVerboseLoggingEnabled`、
  `setDiscDataSourceFactory`（仅保存）、`resetSecondaryTextTrackSelection`（no-op）、
  脚本会话 `setStartupScripts`/`loadScript`/`getScriptData`/`getInputBindings`/
  `unloadScript`/`runScriptBinding`（false/null/no-op，脚本不执行）、
  `setLogListener`/`setAudioFilterListener`（永不触发）、运动插值
  `setMotionInterpolationFps`（存值，返回已完成 Future）/
  `getMotionInterpolationFps`/`isMotionInterpolationSupported`（false）/
  `isMotionInterpolationUpdatePending`（false）/
  `getMotionInterpolationEffectiveTargetFps`/`add/removeMotionInterpolationListener`。
- `libraries/mpvplayer/.../MpvPlayerConfig.java`（第 101 轮）：
  `Builder.setDataSourceFactory(...)`、`setMotionInterpolationFps(...)`（仅保存，
  播放器不消费）+ 对应 getter；第 102 轮补 `Builder.setDefaultFontDirectories(File, File)`
  （仅保存，TV 传两次同一外部字体目录）。
- `libraries/mpvplayer/.../MpvSubtitleOptions.java`（第 101 轮）：
  `Builder.setFontsDirectory/setVerticalOffset/setBitmapSubtitleBrightness/
  setSecondarySubtitlePosition/setSecondaryVerticalOffset/setSecondaryAssStyleOverride/
  setFontFamily/setDefaultStyle`（仅保存，不生效）+ 对应 getter。
- `libraries/mpvplayer/.../MpvLibrary.java`（第 101 轮）：
  `setLibraryLoader(BooleanSupplier)`（仅保存；TV 代码传 `MpvUtil::loadRuntime`，
  `() -> boolean` 方法引用只能是函数式接口，不能是 `LibraryLoader` 类）。
- `libraries/mpvplayer/.../MpvAudioFilter.java`（第 101 轮）：
  `Builder.addDynamicNormalization(...)`（近似映射为 ffmpeg `dynaudnorm` 滤镜）。
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
- 双字幕（副字幕轨）、字幕转录、字幕内容列表：不工作。
- 视频效果（色彩/细节调整）：不生效（直通）。
- 音频均衡器/声道混合：Exo 侧不生效；MPV 侧混音矩阵真实生效。
- ClearKey MP4 无 DRM 会话解密：不工作（走标准 DRM 路径的仍正常）。
- 截尾（跳过片尾）、预加载定位：不生效。
- CMG 流识别：不识别（按用户设置的引擎播放）。
- 启动脚本（mpv 脚本会话）：不执行，TV 侧会把启动脚本标为错误状态。
- 运动插值：不生效（帧率可设可读，但画面不插值）。
- MPV 日志监听 / 音频滤镜监听：永不触发。
- 字幕字体目录、垂直偏移、位图字幕亮度、字体族、默认样式：设置被接受但不生效。
- 动态响度归一化：近似为 ffmpeg `dynaudnorm`，参数映射未经官方实现验证。
- Matroska 延迟 cue 查找标志：定义后被提取器忽略。

## 维护注意

- 上游 FongMi/media 一旦公开这些类（或 TV 代码升级），本目录应删除，
  `install.py` 的补丁锚点会因源码变化而直接失败（不会静默跳过）。
- `install.py` 每个补丁都用精确锚点 + 数量断言；锚点找不到就报错退出。
- 下游 fork `15840213978a/fongmi-android6-main` 的 `restore_official_*.py` 只还原
  UDF/ISO 导航/Dolby Vision 等少数类，不覆盖本轮的 45 个类，故未采用。
