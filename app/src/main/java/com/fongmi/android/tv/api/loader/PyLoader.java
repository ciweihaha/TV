package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.runtime.PythonRuntime;
import com.github.catvod.utils.RuntimeModule;

public class PyLoader extends SpiderLoader {

    private final PythonRuntime loader;

    public PyLoader() {
        this(RuntimeModule.PYTHON.entry(PythonRuntime.class));
    }

    PyLoader(PyLoader previous) {
        this(previous.loader);
    }

    private PyLoader(PythonRuntime loader) {
        loader.initialize();
        this.loader = loader;
    }

    @Override
    protected void clearReferences() {
        recent = null;
    }

    public Spider getSpider(String key, String api, String ext) {
        return load(key, App.get(), ext, () -> newSpider(key, api));
    }

    Spider newSpider(String key, String api) {
        return loader.spider(api, key, JsHost.INSTANCE, JsHost.INSTANCE::getUserAgent);
    }
}
