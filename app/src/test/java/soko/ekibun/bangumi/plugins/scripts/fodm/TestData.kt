package soko.ekibun.bangumi.plugins.scripts.fodm

import soko.ekibun.bangumi.plugins.VideoScriptTest
import soko.ekibun.bangumi.plugins.model.LineInfoModel
import soko.ekibun.bangumi.plugins.model.LineProvider
import soko.ekibun.bangumi.plugins.provider.Provider

class TestData : VideoScriptTest.VideoTestData() {
    /**
     * 线路配置
     */
    override val info = LineProvider.ProviderInfo(
        site = "fodm",
        color = 0x3aade3,
        title = "天使动漫",
        type = Provider.TYPE_VIDEO
    )
    override val searchKey = "日常"
    override val lineInfo = LineInfoModel.LineInfo(
        "fodm",
        id = "/xianwangderichangshenghuo/",
        title = "仙王的日常生活"
    )
}