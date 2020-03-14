package soko.ekibun.bangumi.plugins.scripts.manhuadui

import soko.ekibun.bangumi.plugins.BookScriptTest
import soko.ekibun.bangumi.plugins.model.LineInfoModel
import soko.ekibun.bangumi.plugins.model.LineProvider
import soko.ekibun.bangumi.plugins.provider.Provider
import soko.ekibun.bangumi.plugins.provider.book.BookProvider

class TestData : BookScriptTest.BookTestData() {
    override val info = LineProvider.ProviderInfo(
        site = "manhuadui",
        color = 0x31a4fd,
        title = "漫画堆",
        type = Provider.TYPE_BOOK
    )
    override val searchKey = "刺客守则"
    override val lineInfo = LineInfoModel.LineInfo(
        "manhuadui",
        id = "cikeshouze",
        title = "刺客守则",
        extra = ""
    )
    override val episode = BookProvider.BookEpisode(
        site = "manhuadui",
        id = "279908",
        sort = 1f,
        title = "01话",
        url = "https://m.manhuadui.com/manhua/cikeshouze/279908.html"
    )
}