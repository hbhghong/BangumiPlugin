package soko.ekibun.bangumi.plugins.scripts.agefans

import soko.ekibun.bangumi.plugins.VideoScriptTest
import soko.ekibun.bangumi.plugins.model.line.LineInfo
import soko.ekibun.bangumi.plugins.model.provider.ProviderInfo
import soko.ekibun.bangumi.plugins.provider.Provider

class TestData : VideoScriptTest.VideoTestData() {
    /**
     * 线路配置
     */
    override val info = ProviderInfo(
        site = "agefans",
        color = 0x292929,
        title = "agefans",
        type = Provider.TYPE_VIDEO
    )
    override val searchKey = "轻音少女"
    override val lineInfo = LineInfo(
        "agefans",
        id = "55188-1",
        title = "魔物娘的相伴日常（无修）"
    )
}