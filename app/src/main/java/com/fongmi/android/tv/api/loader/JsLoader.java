package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.runtime.QuickJsRuntime;
import com.github.catvod.utils.RuntimeModule;

import dalvik.system.DexClassLoader;

public class JsLoader extends SpiderLoader {

    private final QuickJsRuntime loader;
    private final QuickJsRuntime.Session module;

    public JsLoader() {
        this(RuntimeModule.QUICKJS.entry(QuickJsRuntime.class));
    }

    JsLoader(JsLoader previous) {
        this(previous.loader);
    }

    private JsLoader(QuickJsRuntime loader) {
        module = loader.session();
        this.loader = loader;
    }

    @Override
    protected void clearReferences() {
        recent = null;
        module.clear();
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return load(key, App.get(), ext, () -> newSpider(key, api, BaseLoader.get().dex(jar)));
    }

    Spider newSpider(String key, String api, DexClassLoader dex) {
        Spider spider = module.spider(api, dex, JsHost.INSTANCE);
        spider.siteKey = key;
        return spider;
    }
}
