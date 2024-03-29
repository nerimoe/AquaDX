package test

import ext.*
import icu.samnyan.aqua.sega.wacca.WaccaServer
import icu.samnyan.aqua.sega.wacca.init
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class WaccaTest : StringSpec({
    val version = "3.07.01.JPN.26935.S"
    var uid = 0L
    var requestNo = 10000

    data class PostResp(val resp: HttpResponse, val res: List<Any>)
    suspend fun post(url: String, par: String): PostResp {
        requestNo++
        val resp = HTTP.post("$HOST/gs/$CLIENT_ID/wacca/api/$url") {
            contentType(ContentType.Application.Json)
            setBody("""{"requestNo": ${requestNo++},"appVersion": "$version","boardId": "$BOARD_ID","chipId": "$FULL_CLIENT_ID","params": $par}""")
        }

        assert(resp.status.isSuccess()) { "Failed to post to $url: ${resp.status} - ${resp.bodyAsText()}" }
        val res = resp.bodyAsText().jsonMap()
        res["status"] shouldBe 0
        res.keys shouldBe setOf("status", "message", "serverTime", "maintNoticeTime", "maintNotPlayableTime", "maintStartTime", "params")

        return PostResp(resp, res["params"] as List<Any>)
    }

    infix fun List<Any?>.exp(expected: List<Any?>) {
        // Replace all timestamps as null
        val start = millis().toString().substring(0..3)
        val lst = this.toJson().replace(Regex("""$start\d{6}(?=[], ])"""), "null").jsonArray()
        val exp = expected.toJson().replace(Regex("""$start\d{6}(?=[], ])"""), "null").jsonArray()
        lst shouldBe exp
    }

    infix fun List<Any?>.exp(json: String) = exp(json.jsonArray())

    infix fun List<Any?>.expGetDetail(json: String) {
        val start = millis().toString().substring(0..3)
        val lst = this.toJson().replace(Regex("""$start\d{6}(?=[], ])"""), "null").jsonArray()
        val exp = json.replace(Regex("""$start\d{6}(?=[], ])"""), "null").jsonArray()

        // Check each ordered element
        listOf(0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17).forEach { i ->
            (lst[i] to i) shouldBe (exp[i] to i)
        }

        // Check each unordered element
        listOf(1, 2, 4, ).forEach { i ->
            ((lst[i] as List<*>).sortedBy { it.toJson() } to i) shouldBe ((exp[i] as List<*>).sortedBy { it.toJson() } to i)
        }

        // Check items (unordered element inside ordered element)
        val setSrc = (lst[3] as List<*>).map { (it as List<*>).sortedBy { it.toJson() } }
        val setExp = (exp[3] as List<*>).map { (it as List<*>).sortedBy { it.toJson() } }
        setSrc shouldBe setExp
    }

    System.getProperty("kotest.assertions.collection.print.size", "1000")

    beforeTest {
        if (uid == 0L) uid = registerUser()
    }

    "API Completion Status" {
        val ws = WaccaServer().apply { init() }
        val defined = (ws.handlerMap.keys + ws.cacheMap.keys).toSortedSet()
        val all = setOf("advertise/GetNews", "advertise/GetRanking", "competition/status/login", "competition/status/update", "housing/get", "housing/start", "user/goods/purchase", "user/info/getMyroom", "user/info/getRanking", "user/info/update", "user/mission/update", "user/music/unlock", "user/music/updateCoop", "user/music/updateTrial", "user/music/updateVersus", "user/music/update", "user/rating/update", "user/status/create", "user/status/getDetail", "user/status/get", "user/status/login", "user/status/logout", "user/status/update", "user/sugoroku/update", "user/trial/get", "user/trial/update", "user/vip/get", "user/vip/start").map { it.lowercase() }.toSortedSet()
        all shouldBe defined
    }

    "housing/get #1" {
        post("housing/get", "[]").res exp "[39, 0]"
    }

    "housing/start #1" {
        post("housing/start", """["", "2024/03/24 10:39:36, ApiUserStatusLogout,0\\n2024/03/24 10:51:06, ApiUserStatusLogout,0\\n2024/03/24 10:54:19, ApiUserStatusLogout,0\\n2024/03/24 10:59:33, ApiAdvertiseGetNews,0\\n2024/03/24 11:10:31, ApiAdvertiseGetNews,0\\n2024/03/24 11:11:04, ApiUserStatusLogout,0\\n2024/03/24 11:19:51, ,0\\n2024/03/24 11:20:14, ApiAdvertiseGetNews,0\\n", "", [[1, "SERVER"], [2, "JPN"]]]""").res exp
            "[1, [1269, 1007, 1270, 1002, 1020, 1003, 1008, 1211, 1018, 1092, 1056, 32, 1260, 1230, 1258, 1251, 2212, 1264, 1125, 1037, 2001, 1272, 1126, 1119, 1104, 1070, 1047, 1044, 1027, 1004, 1001, 24, 2068, 2062, 2021, 1275, 1249, 1207, 1203, 1107, 1021, 1009, 9, 4, 3, 23, 22, 2014, 13, 1276, 1247, 1240, 1237, 1128, 1114, 1110, 1109, 1102, 1045, 1043, 1036, 1035, 1030, 1023, 1015]]"
    }

    "advertise/GetNews #1" {
        post("advertise/GetNews", "[]").res exp
            "[[], [], [], [], [], [], [], [], []]"
    }

    "user/status/get #1" {
        post("user/status/get", """["$uid"]""").res exp
            """[[0, "", 1, 0, 0, 0, 500, [0, 0, 0], 0, 0, 0, 0, 3376684800, 0, 0], 104001, 102001, 1, [2, "1.0.0"], []]"""
    }

    "user/status/create #1" {
        post("user/status/create", """["$uid", "AZA"]""").res exp
            """[[$uid, "AZA", 1, 0, 0, 0, 500, [0, 0, 0], 0, 0, 0, 0, 3376684800, 0, 0]]"""
    }

    "user/status/login Guest" {
        post("user/status/login", "[0]").res exp
            "[[], [], [], 0, [2077, 1, 1, 1, [], []], 0, []]"
    }

    "user/status/login #2" {
        post("user/status/login", "[$uid]").res exp
            "[[], [], [], 0, [2077, 1, 1, 1, [], []], null, []]"
    }

    "user/status/getDetail #1" {
        post("user/status/getDetail", "[$uid]").res expGetDetail
            """[[$uid, "AZA", 1, 0, 0, 0, 500, [0, 0, 0], 1, 1, 0, 1, 3376684800, 1, 0], [], [[3, 1, 0], [3, 2, 0], [3, 3, 0], [3, 4, 0], [3, 5, 0], [0, 1, 1]], [[], [[104001, 1, null], [104002, 1, null], [104003, 1, null], [104005, 1, null]], [[102001, 1, 0, null], [102002, 1, 0, null]], [], [], [], [[103001, 1, null], [203001, 1, null]], [[105001, 1, null], [205005, 1, null]], [[210001, 1, null, 0, 0], [210002, 1, null, 0, 0], [210054, 1, null, 0, 0], [210055, 1, null, 0, 0], [210056, 1, null, 0, 0], [210057, 1, null, 0, 0], [210058, 1, null, 0, 0], [210059, 1, null, 0, 0], [210060, 1, null, 0, 0], [210061, 1, null, 0, 0], [310001, 1, null, 0, 0], [310002, 1, null, 0, 0]], [[211001, 1, null]], [[312000, 1, null], [312001, 1, null]]], [], [0, 1], [0, 500, 0, 0, 4, 2, 0, 2, 2, 1, 0], [[0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0]], null, [], [], [], [[1, 1, 1, 0, 0, 0, 0], [2, 1, 1, 0, 0, 0, 0], [3, 1, 1, 0, 0, 0, 0], [4, 1, 1, 0, 0, 0, 0], [5, 1, 1, 0, 0, 0, 0], [6, 1, 1, 0, 0, 0, 0], [7, 1, 1, 0, 0, 0, 0], [8, 1, 1, 0, 0, 0, 0], [9, 1, 1, 0, 0, 0, 0], [10, 1, 1, 0, 0, 0, 0], [11, 1, 1, 0, 0, 0, 0], [12, 1, 1, 0, 0, 0, 0], [13, 1, 1, 0, 0, 0, 0], [14, 1, 1, 0, 0, 0, 0], [15, 1, 1, 0, 0, 0, 0], [16, 1, 1, 0, 0, 0, 0], [17, 1, 1, 0, 0, 0, 0], [18, 1, 1, 0, 0, 0, 0], [19, 1, 1, 0, 0, 0, 0], [20, 1, 1, 0, 0, 0, 0], [21, 1, 1, 0, 0, 0, 0], [22, 1, 1, 0, 0, 0, 0], [23, 1, 1, 0, 0, 0, 0], [24, 1, 1, 0, 0, 0, 0]], [0, 0, 0, 0, 0], [[1, 0], [2, 0], [3, 0], [4, 0], [5, 0]], [], [], [0, []]]"""
    }

    "user/sugoroku/update #1" {
        post("user/sugoroku/update", "[$uid, 23, 2, 42, 0, 0, [[6, 302027, 1]], 292, 0]").res exp
            "[]"
    }

    "user/mission/update #1" {
        post("user/mission/update", "[$uid, [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 0], [4, 5, 0], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 812201]]], [], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]]]").res exp
            "[]"
    }

    "user/music/update #4" {
        post("user/music/update", "[$uid, 1, [1116, 2, 9.699999809265137, 812201, [252, 105, 28, 33], 56, 5, 1, 0, 0, 0, 0, 0, 11, 120, 1], [[2, 0, 936]]]").res exp
            "[[1116, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0], 812201, 33, 0, 1, 0], [1116, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/sugoroku/update #2" {
        post("user/sugoroku/update", "[$uid, 23, 3, 86, 0, 0, [[5, 304129, 1]], 324, 0]").res exp
            "[]"
    }

    "user/mission/update #2" {
        post("user/mission/update", "[$uid, [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 0], [4, 5, 0], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 1715547]]], [], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]]]").res exp
            "[]"
    }

    "user/music/update #5" {
        post("user/music/update", "[$uid, 2, [2074, 2, 9.100000381469727, 903346, [212, 40, 6, 11], 77, 7, 1, 0, 0, 0, 0, 0, 1, 45, 1], [[2, 0, 1224], [4, 2074, 3]]]").res exp
            "[[2074, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 903346, 11, 0, 1, 91], [2074, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/sugoroku/update #3" {
        post("user/sugoroku/update", "[$uid, 23, 4, 90, 0, 0, [[6, 302030, 1]], 324, 0]").res exp
            "[]"
    }

    "user/mission/update #3" {
        post("user/mission/update", "[$uid, [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 0], [4, 5, 0], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]], [], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]]]").res exp
            "[]"
    }

    "user/music/update #6" {
        post("user/music/update", "[$uid, 3, [1111, 2, 10.899999618530273, 900776, [484, 128, 13, 19], 157, 7, 1, 0, 0, 0, 0, 0, 15, 126, 1], [[2, 0, 1224], [4, 1111, 3]]]").res exp
            "[[1111, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 900776, 19, 0, 1, 108], [1111, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/status/update #2" {
        post("user/status/update", "[$uid, 1, [[1, 0, 100], [2, 0, 250], [5, 104078, 1], [5, 104125, 1], [7, 301001, 1000], [7, 301002, 300], [7, 301003, 100]], 0, 0, [], [1111, 2, 1, 5, 2]]").res exp
            "[]"
    }

    "user/rating/update #1" {
        post("user/rating/update", "[$uid, 497, [[1116, 2, 97], [2074, 3, 0], [2074, 2, 182], [1111, 3, 0], [1111, 2, 218]]]").res exp
            "[]"
    }

    "user/info/getMyroom #1" {
        post("user/info/getMyroom", "[$uid]").res exp
            "[0, 0, 0, 0, 0, [], 0, 0, 0]"
    }

    "user/info/update #1" {
        post("user/info/update", "[$uid, [[1, 38], [2, 3], [108, 94]], [], [], [], []]").res exp
            "[]"
    }

    "user/status/logout #2" {
        post("user/status/logout", "[$uid]").res exp
            "[]"
    }

    "user/status/get #2" {
        post("user/status/get", """["$uid"]""").res exp
            """[[$uid, "AZA", 1, 100, 0, 0, 4134, [0, 0, 0], 1, 1, 0, 1, 3376684800, 1, 497], 104001, 102001, 0, [0, "3.7.1"], [[1, 38], [2, 3], [108, 94]]]"""
    }

    "user/status/login #3" {
        post("user/status/login", "[$uid]").res exp
            "[[], [], [], 0, [2077, 1, 1, 1, [], []], null, []]"
    }

    "user/status/getDetail #2" {
        post("user/status/getDetail", "[$uid]").res expGetDetail
            """[[$uid, "AZA", 1, 100, 0, 0, 4134, [0, 0, 0], 2, 1, 1, 1, 3376684800, 2, 497], [[1, 38], [2, 3], [108, 94]], [[3, 1, 1], [3, 2, 0], [3, 3, 0], [3, 4, 0], [3, 5, 0], [0, 1, 1]], [[[1111, 1, 0, 1711419516], [1111, 2, 0, 1711419516], [1111, 3, 0, 1711419516], [2074, 1, 0, 1711419238], [2074, 2, 0, 1711419238], [2074, 3, 0, 1711419238]], [[104001, 1, 1711418627], [104002, 1, 1711418627], [104003, 1, 1711418627], [104005, 1, 1711418627], [304129, 1, 1711419172], [104078, 1, 1711419526], [104125, 1, 1711419526]], [[102001, 1, 1, 1711418627], [102002, 1, 0, 1711418627], [302027, 1, 0, 1711418927], [302030, 1, 0, 1711419486]], [[301001, 3, 1000, 0], [301002, 3, 300, 0], [301003, 3, 100, 0]], [], [], [[103001, 1, 1711418627], [203001, 1, 1711418627]], [[105001, 1, 1711418627], [205005, 1, 1711418627]], [[210001, 1, 1711418627, 0, 0], [210002, 1, 1711418627, 0, 0], [210054, 1, 1711418627, 0, 0], [210055, 1, 1711418627, 0, 0], [210056, 1, 1711418627, 0, 0], [210057, 1, 1711418627, 0, 0], [210058, 1, 1711418627, 0, 0], [210059, 1, 1711418627, 0, 0], [210060, 1, 1711418627, 0, 0], [210061, 1, 1711418627, 0, 0], [310001, 1, 1711418627, 1, 1], [310002, 1, 1711418627, 0, 0]], [[211001, 1, 1711418627]], [[312000, 1, 1711418627], [312001, 1, 1711418627]]], [[1116, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0], 812201, 56, 33, 1, 97], [2074, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 903346, 77, 11, 1, 182], [1111, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 900776, 157, 19, 1, 218]], [1111, 1], [100, 4134, 0, 2616323, 7, 4, 0, 2, 2, 1, 324], [[0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0]], 1711422586, [], [], [], [[1, 1, 1, 0, 0, 0, 0], [2, 1, 1, 0, 0, 0, 0], [3, 1, 1, 0, 0, 0, 0], [4, 1, 1, 0, 0, 0, 0], [5, 1, 1, 0, 0, 0, 0], [6, 1, 1, 0, 0, 0, 0], [7, 1, 1, 0, 0, 0, 0], [8, 1, 1, 0, 0, 0, 0], [9, 1, 1, 0, 0, 0, 0], [10, 1, 1, 0, 0, 0, 0], [11, 1, 1, 0, 0, 0, 0], [12, 1, 1, 0, 0, 0, 0], [13, 1, 1, 0, 0, 0, 0], [14, 1, 1, 0, 0, 0, 0], [15, 1, 1, 0, 0, 0, 0], [16, 1, 1, 0, 0, 0, 0], [17, 1, 1, 0, 0, 0, 0], [18, 1, 1, 0, 0, 0, 0], [19, 1, 1, 0, 0, 0, 0], [20, 1, 1, 0, 0, 0, 0], [21, 1, 1, 0, 0, 0, 0], [22, 1, 1, 0, 0, 0, 0], [23, 1, 4, 90, 0, 1711418927, 0], [24, 1, 1, 0, 0, 0, 0]], [1111, 2, 1, 5, 2], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]], [], [], [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 0], [4, 5, 0], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]]]"""
    }

    "user/status/get #3" {
        post("user/status/get", """["$uid"]""").res exp
            """[[$uid, "AZA", 1, 100, 0, 0, 4134, [0, 0, 0], 2, 1, 1, 1, 3376684800, 2, 497], 104001, 102001, 0, [0, "3.7.1"], [[1, 38], [2, 3], [108, 94]]]"""
    }

    "user/status/login #4" {
        post("user/status/login", "[$uid]").res exp
            "[[], [], [], 0, [2077, 1, 1, 1, [], []], null, []]"
    }

    "user/status/getDetail #3" {
        post("user/status/getDetail", "[$uid]").res expGetDetail
            """[[$uid, "AZA", 1, 100, 0, 0, 4134, [0, 0, 0], 3, 1, 2, 1, 3376684800, 3, 497], [[1, 38], [2, 3], [108, 94]], [[3, 1, 1], [3, 2, 0], [3, 3, 0], [3, 4, 0], [3, 5, 0], [0, 1, 1]], [[[1111, 1, 0, 1711419516], [1111, 2, 0, 1711419516], [1111, 3, 0, 1711419516], [2074, 1, 0, 1711419238], [2074, 2, 0, 1711419238], [2074, 3, 0, 1711419238]], [[104001, 1, 1711418627], [104002, 1, 1711418627], [104003, 1, 1711418627], [104005, 1, 1711418627], [304129, 1, 1711419172], [104078, 1, 1711419526], [104125, 1, 1711419526]], [[102001, 1, 1, 1711418627], [102002, 1, 0, 1711418627], [302027, 1, 0, 1711418927], [302030, 1, 0, 1711419486]], [[301001, 3, 1000, 0], [301002, 3, 300, 0], [301003, 3, 100, 0]], [], [], [[103001, 1, 1711418627], [203001, 1, 1711418627]], [[105001, 1, 1711418627], [205005, 1, 1711418627]], [[210001, 1, 1711418627, 0, 0], [210002, 1, 1711418627, 0, 0], [210054, 1, 1711418627, 0, 0], [210055, 1, 1711418627, 0, 0], [210056, 1, 1711418627, 0, 0], [210057, 1, 1711418627, 0, 0], [210058, 1, 1711418627, 0, 0], [210059, 1, 1711418627, 0, 0], [210060, 1, 1711418627, 0, 0], [210061, 1, 1711418627, 0, 0], [310001, 1, 1711418627, 1, 1], [310002, 1, 1711418627, 0, 0]], [[211001, 1, 1711418627]], [[312000, 1, 1711418627], [312001, 1, 1711418627]]], [[1116, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0], 812201, 56, 33, 1, 97], [2074, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 903346, 77, 11, 1, 182], [1111, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 900776, 157, 19, 1, 218]], [1111, 1], [100, 4134, 0, 2616323, 7, 4, 0, 2, 2, 1, 324], [[0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0]], 1711422954, [], [], [], [[1, 1, 1, 0, 0, 0, 0], [2, 1, 1, 0, 0, 0, 0], [3, 1, 1, 0, 0, 0, 0], [4, 1, 1, 0, 0, 0, 0], [5, 1, 1, 0, 0, 0, 0], [6, 1, 1, 0, 0, 0, 0], [7, 1, 1, 0, 0, 0, 0], [8, 1, 1, 0, 0, 0, 0], [9, 1, 1, 0, 0, 0, 0], [10, 1, 1, 0, 0, 0, 0], [11, 1, 1, 0, 0, 0, 0], [12, 1, 1, 0, 0, 0, 0], [13, 1, 1, 0, 0, 0, 0], [14, 1, 1, 0, 0, 0, 0], [15, 1, 1, 0, 0, 0, 0], [16, 1, 1, 0, 0, 0, 0], [17, 1, 1, 0, 0, 0, 0], [18, 1, 1, 0, 0, 0, 0], [19, 1, 1, 0, 0, 0, 0], [20, 1, 1, 0, 0, 0, 0], [21, 1, 1, 0, 0, 0, 0], [22, 1, 1, 0, 0, 0, 0], [23, 1, 4, 90, 0, 1711418927, 0], [24, 1, 1, 0, 0, 0, 0]], [1111, 2, 1, 5, 2], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]], [], [], [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 0], [4, 5, 0], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]]]"""
    }

    "user/sugoroku/update #4" {
        post("user/sugoroku/update", "[$uid, 23, 5, 79, 0, 0, [[5, 304132, 1]], 339, 0]").res exp
            "[]"
    }

    "user/music/update #7" {
        post("user/music/update", "[$uid, 1, [1111, 2, 10.899999618530273, 944410, [556, 71, 5, 12], 191, 11, 1, 0, 0, 0, 0, 0, 25, 51, 1], [[2, 0, 1224]]]").res exp
            "[[1111, 2, [2, 2, 0, 0, 0], [2, 2, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0], 944410, 12, 0, 1, 299], [1111, 2], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/music/unlock #1" {
        post("user/music/unlock", "[$uid, 1085, 3, [[9, 106002, 1]]]").res exp
            "[5358, []]"
    }

    "user/status/get #4" {
        post("user/status/get", """["$uid"]""").res exp
            """[[$uid, "AZA", 1, 100, 0, 0, 5358, [0, 0, 0], 3, 1, 2, 1, 3376684800, 3, 497], 104001, 102001, 0, [0, "3.7.1"], [[1, 38], [2, 3], [108, 94]]]"""
    }

    "user/status/login #5" {
        post("user/status/login", "[$uid]").res exp
            "[[], [], [], 0, [2077, 1, 1, 1, [], []], 1711422954, []]"
    }

    "user/status/getDetail #4" {
        post("user/status/getDetail", "[$uid]").res expGetDetail
            """[[$uid, "AZA", 1, 100, 0, 0, 5358, [0, 0, 0], 4, 1, 3, 1, 3376684800, 4, 497], [[1, 38], [2, 3], [108, 94]], [[3, 1, 1], [3, 2, 0], [3, 3, 0], [3, 4, 0], [3, 5, 0], [0, 1, 1]], [[[2074, 1, 0, 1711419238], [2074, 2, 0, 1711419238], [2074, 3, 0, 1711419238], [1111, 1, 0, 1711419516], [1111, 2, 0, 1711419516], [1111, 3, 0, 1711419516], [1085, 1, 0, 1711423274], [1085, 2, 0, 1711423274], [1085, 3, 0, 1711423274]], [[104001, 1, 1711418627], [104002, 1, 1711418627], [104003, 1, 1711418627], [104005, 1, 1711418627], [304129, 1, 1711419172], [104078, 1, 1711419526], [104125, 1, 1711419526], [304132, 1, 1711423159]], [[102001, 1, 1, 1711418627], [102002, 1, 0, 1711418627], [302027, 1, 0, 1711418927], [302030, 1, 0, 1711419486]], [[301001, 3, 1000, 0], [301002, 3, 300, 0], [301003, 3, 100, 0]], [], [], [[103001, 1, 1711418627], [203001, 1, 1711418627]], [[105001, 1, 1711418627], [205005, 1, 1711418627]], [[210001, 1, 1711418627, 0, 0], [210002, 1, 1711418627, 0, 0], [210054, 1, 1711418627, 0, 0], [210055, 1, 1711418627, 0, 0], [210056, 1, 1711418627, 0, 0], [210057, 1, 1711418627, 0, 0], [210058, 1, 1711418627, 0, 0], [210059, 1, 1711418627, 0, 0], [210060, 1, 1711418627, 0, 0], [210061, 1, 1711418627, 0, 0], [310001, 1, 1711418627, 1, 1], [310002, 1, 1711418627, 0, 0]], [[211001, 1, 1711418627]], [[312000, 1, 1711418627], [312001, 1, 1711418627]]], [[1116, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0], 812201, 56, 33, 1, 97], [2074, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 903346, 77, 11, 1, 182], [1111, 2, [2, 2, 0, 0, 0], [2, 2, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0], 944410, 191, 12, 1, 218]], [1111, 1], [100, 5358, 0, 2659957, 8, 4, 0, 2, 2, 1, 339], [[0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0]], 1711423417, [], [], [], [[1, 1, 1, 0, 0, 0, 0], [2, 1, 1, 0, 0, 0, 0], [3, 1, 1, 0, 0, 0, 0], [4, 1, 1, 0, 0, 0, 0], [5, 1, 1, 0, 0, 0, 0], [6, 1, 1, 0, 0, 0, 0], [7, 1, 1, 0, 0, 0, 0], [8, 1, 1, 0, 0, 0, 0], [9, 1, 1, 0, 0, 0, 0], [10, 1, 1, 0, 0, 0, 0], [11, 1, 1, 0, 0, 0, 0], [12, 1, 1, 0, 0, 0, 0], [13, 1, 1, 0, 0, 0, 0], [14, 1, 1, 0, 0, 0, 0], [15, 1, 1, 0, 0, 0, 0], [16, 1, 1, 0, 0, 0, 0], [17, 1, 1, 0, 0, 0, 0], [18, 1, 1, 0, 0, 0, 0], [19, 1, 1, 0, 0, 0, 0], [20, 1, 1, 0, 0, 0, 0], [21, 1, 1, 0, 0, 0, 0], [22, 1, 1, 0, 0, 0, 0], [23, 1, 5, 79, 0, 1711418927, 0], [24, 1, 1, 0, 0, 0, 0]], [1111, 2, 1, 5, 2], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]], [], [], [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 0], [4, 5, 0], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]]]"""
    }

    "user/music/unlock #2" {
        post("user/music/unlock", "[$uid, 3011, 3, [[9, 106002, 1]]]").res exp
            "[5358, []]"
    }

    "user/music/unlock #3" {
        post("user/music/unlock", "[$uid, 2009, 3, [[9, 106002, 1]]]").res exp
            "[5358, []]"
    }

    "user/sugoroku/update #5" {
        post("user/sugoroku/update", "[$uid, 23, 5, 313, 0, 0, [], 234, 0]").res exp
            "[]"
    }

    "user/mission/update #4" {
        post("user/mission/update", "[$uid, [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 1], [4, 5, 0], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]], [], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]]]").res exp
            "[]"
    }

    "user/music/update #8" {
        post("user/music/update", "[$uid, 1, [2009, 3, 13.699999809265137, 657762, [464, 225, 62, 241], 48, 3, 0, 0, 0, 0, 0, 0, 171, 108, 1], [[2, 0, 252]]]").res exp
            "[[2009, 3, [1, 0, 0, 0, 0], [1, 0, 0, 0, 0], [0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 657762, 241, 0, 1, 0], [2009, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/sugoroku/update #6" {
        post("user/sugoroku/update", "[$uid, 23, 6, 279, 0, 0, [[6, 302033, 1]], 346, 0]").res exp
            "[]"
    }

    "user/mission/update #5" {
        post("user/mission/update", "[$uid, [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 1], [4, 5, 1], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]], [[2, 0, 100]], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]]]").res exp
            "[]"
    }

    "user/music/update #9" {
        post("user/music/update", "[$uid, 2, [4, 2, 10.899999618530273, 967339, [464, 19, 5, 8], 246, 8, 1, 0, 0, 0, 0, 0, 16, 8, 1], [[2, 0, 1296], [4, 4, 3]]]").res exp
            "[[4, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0], 967339, 8, 0, 1, 354], [4, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/sugoroku/update #7" {
        post("user/sugoroku/update", "[$uid, 23, 7, 205, 0, 0, [[5, 304135, 1]], 346, 0]").res exp
            "[]"
    }

    "user/mission/update #6" {
        post("user/mission/update", "[$uid, [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 1], [4, 5, 1], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]], [], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]]]").res exp
            "[]"
    }

    "user/music/update #10" {
        post("user/music/update", "[$uid, 3, [4, 2, 10.899999618530273, 962702, [451, 35, 4, 6], 199, 8, 1, 0, 0, 0, 0, 0, 35, 4, 0], [[2, 0, 1296]]]").res exp
            "[[4, 2, [2, 2, 0, 0, 0], [2, 2, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0], 967339, 6, 0, 1, 354], [4, 2], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/status/update #3" {
        post("user/status/update", "[$uid, 1, [[1, 0, 100], [2, 0, 250], [6, 102023, 1], [7, 301001, 2333], [7, 301002, 700], [7, 301003, 233], [7, 301007, 1000], [7, 301008, 200]], 0, 0, [], [4, 2, 1, 7, 5]]").res exp
            "[]"
    }

    "user/rating/update #2" {
        post("user/rating/update", "[$uid, 1028, [[1111, 2, 299], [3011, 3, 0], [2009, 3, 95], [4, 3, 0], [4, 2, 354]]]").res exp
            "[]"
    }

    "user/info/update #2" {
        post("user/info/update", "[$uid, [[1, 40], [108, 100]], [], [], [], []]").res exp
            "[]"
    }

    "user/status/get #5" {
        post("user/status/get", """["$uid"]""").res exp
            """[[$uid, "AZA", 1, 200, 0, 0, 8552, [0, 0, 0], 4, 1, 3, 1, 3376684800, 4, 1028], 104001, 102001, 0, [0, "3.7.1"], [[1, 40], [2, 3], [108, 100]]]"""
    }

    "user/status/login #6" {
        post("user/status/login", "[$uid]").res exp
            "[[], [], [], 0, [2077, 1, 1, 1, [], []], 1711423417, []]"
    }

    "user/status/getDetail #5" {
        post("user/status/getDetail", "[$uid]").res expGetDetail
            """[[$uid, "AZA", 1, 200, 0, 0, 8552, [0, 0, 0], 5, 1, 4, 1, 3376684800, 5, 1028], [[1, 40], [2, 3], [108, 100]], [[3, 1, 2], [3, 2, 0], [3, 3, 0], [3, 4, 0], [3, 5, 0], [0, 1, 1]], [[[2074, 1, 0, 1711419238], [2074, 2, 0, 1711419238], [2074, 3, 0, 1711419238], [1111, 1, 0, 1711419516], [1111, 2, 0, 1711419516], [1111, 3, 0, 1711419516], [1085, 1, 0, 1711423274], [1085, 2, 0, 1711423274], [1085, 3, 0, 1711423274], [3011, 1, 0, 1711423444], [3011, 2, 0, 1711423444], [3011, 3, 0, 1711423444], [2009, 1, 0, 1711423495], [2009, 2, 0, 1711423495], [2009, 3, 0, 1711423495], [4, 1, 0, 1711423942], [4, 2, 0, 1711423942], [4, 3, 0, 1711423942]], [[104001, 1, 1711418627], [104002, 1, 1711418627], [104003, 1, 1711418627], [104005, 1, 1711418627], [304129, 1, 1711419172], [104078, 1, 1711419526], [104125, 1, 1711419526], [304132, 1, 1711423159], [304135, 1, 1711424222]], [[102001, 1, 2, 1711418627], [102002, 1, 0, 1711418627], [302027, 1, 0, 1711418927], [302030, 1, 0, 1711419486], [302033, 1, 0, 1711423919], [102023, 1, 0, 1711424255]], [[301001, 3, 2333, 0], [301002, 3, 700, 0], [301003, 3, 233, 0], [301007, 3, 1000, 0], [301008, 3, 200, 0]], [], [], [[103001, 1, 1711418627], [203001, 1, 1711418627]], [[105001, 1, 1711418627], [205005, 1, 1711418627]], [[210001, 1, 1711418627, 0, 0], [210002, 1, 1711418627, 0, 0], [210054, 1, 1711418627, 0, 0], [210055, 1, 1711418627, 0, 0], [210056, 1, 1711418627, 0, 0], [210057, 1, 1711418627, 0, 0], [210058, 1, 1711418627, 0, 0], [210059, 1, 1711418627, 0, 0], [210060, 1, 1711418627, 0, 0], [210061, 1, 1711418627, 0, 0], [310001, 1, 1711418627, 2, 2], [310002, 1, 1711418627, 0, 0]], [[211001, 1, 1711418627]], [[312000, 1, 1711418627], [312001, 1, 1711418627]]], [[1116, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0], 812201, 56, 33, 1, 97], [2074, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 903346, 77, 11, 1, 182], [1111, 2, [2, 2, 0, 0, 0], [2, 2, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0], 944410, 191, 12, 1, 299], [2009, 3, [1, 0, 0, 0, 0], [1, 0, 0, 0, 0], [0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 657762, 48, 0, 1, 95], [4, 2, [2, 2, 0, 0, 0], [2, 2, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0], 967339, 246, 6, 1, 354]], [4, 1], [200, 8552, 0, 4285058, 9, 6, 0, 2, 2, 1, 346], [[0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0]], 1711425683, [], [], [], [[1, 1, 1, 0, 0, 0, 0], [2, 1, 1, 0, 0, 0, 0], [3, 1, 1, 0, 0, 0, 0], [4, 1, 1, 0, 0, 0, 0], [5, 1, 1, 0, 0, 0, 0], [6, 1, 1, 0, 0, 0, 0], [7, 1, 1, 0, 0, 0, 0], [8, 1, 1, 0, 0, 0, 0], [9, 1, 1, 0, 0, 0, 0], [10, 1, 1, 0, 0, 0, 0], [11, 1, 1, 0, 0, 0, 0], [12, 1, 1, 0, 0, 0, 0], [13, 1, 1, 0, 0, 0, 0], [14, 1, 1, 0, 0, 0, 0], [15, 1, 1, 0, 0, 0, 0], [16, 1, 1, 0, 0, 0, 0], [17, 1, 1, 0, 0, 0, 0], [18, 1, 1, 0, 0, 0, 0], [19, 1, 1, 0, 0, 0, 0], [20, 1, 1, 0, 0, 0, 0], [21, 1, 1, 0, 0, 0, 0], [22, 1, 1, 0, 0, 0, 0], [23, 1, 7, 205, 0, 1711418927, 0], [24, 1, 1, 0, 0, 0, 0]], [4, 2, 1, 7, 5], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]], [], [], [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 1], [4, 5, 1], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]]]"""
    }

    "user/sugoroku/update #8" {
        post("user/sugoroku/update", "[$uid, 23, 7, 205, 0, 0, [], 0, 0]").res exp
            "[]"
    }

    "user/music/update #11" {
        post("user/music/update", "[$uid, 1, [4, 3, 13.300000190734863, 8353, [5, 1, 3, 853], 1, 2, 0, 0, 0, 0, 0, 0, 0, 2, 1], [[2, 0, 180]]]").res exp
            "[[4, 3, [1, 0, 0, 0, 0], [1, 0, 0, 0, 0], [0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 8353, 853, 0, 1, 0], [4, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/status/get #6" {
        post("user/status/get", """["$uid"]""").res exp
            """[[$uid, "AZA", 1, 200, 0, 0, 8732, [0, 0, 0], 5, 1, 4, 1, 3376684800, 5, 1028], 104001, 102001, 0, [0, "3.7.1"], [[1, 40], [2, 3], [108, 100]]]"""
    }

    "user/status/login #7" {
        post("user/status/login", "[$uid]").res exp
            "[[], [], [], 0, [2077, 1, 1, 1, [], []], 1711425683, []]"
    }

    "user/status/getDetail #6" {
        post("user/status/getDetail", "[$uid]").res expGetDetail
            """[[$uid, "AZA", 1, 200, 0, 0, 8732, [0, 0, 0], 6, 1, 5, 1, 3376684800, 6, 1028], [[1, 40], [2, 3], [108, 100]], [[3, 1, 2], [3, 2, 0], [3, 3, 0], [3, 4, 0], [3, 5, 0], [0, 1, 1]], [[[2074, 1, 0, 1711419238], [2074, 2, 0, 1711419238], [2074, 3, 0, 1711419238], [1111, 1, 0, 1711419516], [1111, 2, 0, 1711419516], [1111, 3, 0, 1711419516], [1085, 1, 0, 1711423274], [1085, 2, 0, 1711423274], [1085, 3, 0, 1711423274], [3011, 1, 0, 1711423444], [3011, 2, 0, 1711423444], [3011, 3, 0, 1711423444], [2009, 1, 0, 1711423495], [2009, 2, 0, 1711423495], [2009, 3, 0, 1711423495], [4, 1, 0, 1711423942], [4, 2, 0, 1711423942], [4, 3, 0, 1711423942]], [[104001, 1, 1711418627], [104002, 1, 1711418627], [104003, 1, 1711418627], [104005, 1, 1711418627], [304129, 1, 1711419172], [104078, 1, 1711419526], [104125, 1, 1711419526], [304132, 1, 1711423159], [304135, 1, 1711424222]], [[102001, 1, 2, 1711418627], [102002, 1, 0, 1711418627], [302027, 1, 0, 1711418927], [302030, 1, 0, 1711419486], [302033, 1, 0, 1711423919], [102023, 1, 0, 1711424255]], [[301001, 3, 2333, 0], [301002, 3, 700, 0], [301003, 3, 233, 0], [301007, 3, 1000, 0], [301008, 3, 200, 0]], [], [], [[103001, 1, 1711418627], [203001, 1, 1711418627]], [[105001, 1, 1711418627], [205005, 1, 1711418627]], [[210001, 1, 1711418627, 0, 0], [210002, 1, 1711418627, 0, 0], [210054, 1, 1711418627, 0, 0], [210055, 1, 1711418627, 0, 0], [210056, 1, 1711418627, 0, 0], [210057, 1, 1711418627, 0, 0], [210058, 1, 1711418627, 0, 0], [210059, 1, 1711418627, 0, 0], [210060, 1, 1711418627, 0, 0], [210061, 1, 1711418627, 0, 0], [310001, 1, 1711418627, 2, 2], [310002, 1, 1711418627, 0, 0]], [[211001, 1, 1711418627]], [[312000, 1, 1711418627], [312001, 1, 1711418627]]], [[1116, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0], 812201, 56, 33, 1, 97], [2074, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 903346, 77, 11, 1, 182], [1111, 2, [2, 2, 0, 0, 0], [2, 2, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0], 944410, 191, 12, 1, 299], [2009, 3, [1, 0, 0, 0, 0], [1, 0, 0, 0, 0], [0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 657762, 48, 0, 1, 95], [4, 3, [1, 0, 0, 0, 0], [1, 0, 0, 0, 0], [0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 8353, 1, 0, 1, 0], [4, 2, [2, 2, 0, 0, 0], [2, 2, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0], 967339, 246, 6, 1, 354]], [4, 1], [200, 8232, 0, 4293411, 9, 6, 0, 2, 2, 1, 0], [[0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0], [0]], 1711426091, [], [], [], [[1, 1, 1, 0, 0, 0, 0], [2, 1, 1, 0, 0, 0, 0], [3, 1, 1, 0, 0, 0, 0], [4, 1, 1, 0, 0, 0, 0], [5, 1, 1, 0, 0, 0, 0], [6, 1, 1, 0, 0, 0, 0], [7, 1, 1, 0, 0, 0, 0], [8, 1, 1, 0, 0, 0, 0], [9, 1, 1, 0, 0, 0, 0], [10, 1, 1, 0, 0, 0, 0], [11, 1, 1, 0, 0, 0, 0], [12, 1, 1, 0, 0, 0, 0], [13, 1, 1, 0, 0, 0, 0], [14, 1, 1, 0, 0, 0, 0], [15, 1, 1, 0, 0, 0, 0], [16, 1, 1, 0, 0, 0, 0], [17, 1, 1, 0, 0, 0, 0], [18, 1, 1, 0, 0, 0, 0], [19, 1, 1, 0, 0, 0, 0], [20, 1, 1, 0, 0, 0, 0], [21, 1, 1, 0, 0, 0, 0], [22, 1, 1, 0, 0, 0, 0], [23, 1, 7, 205, 0, 1711418927, 0], [24, 1, 1, 0, 0, 0, 0]], [4, 2, 1, 7, 5], [[1, 1], [2, 1], [3, 1], [4, 1], [5, 0]], [], [], [1, [[0, 1, 1], [1, 2, 0], [2, 3, 0], [3, 4, 1], [4, 5, 1], [5, 6, 0], [6, 7, 0], [7, 8, 800], [8, 9, 2000000]]]]"""
    }

    "user/sugoroku/update #9" {
        post("user/sugoroku/update", "[$uid, 23, 8, 87, 0, 0, [[6, 302028, 1]], 332, 0]").res exp
            "[]"
    }

    "user/music/update #12" {
        post("user/music/update", "[$uid, 1, [1056, 2, 10.600000381469727, 928060, [455, 49, 7, 20], 133, 7, 1, 0, 0, 0, 0, 0, 15, 41, 1], [[2, 0, 1224], [4, 1056, 3]]]").res exp
            "[[1056, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0], 928060, 20, 0, 1, 238], [1056, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/music/unlock #4" {
        post("user/music/unlock", "[$uid, 2235, 0, [[2, 0, 5000]]]").res exp
            "[4956, []]"
    }

    "user/sugoroku/update #10" {
        post("user/sugoroku/update", "[$uid, 23, 8, 393, 0, 0, [], 306, 0]").res exp
            "[]"
    }

    "user/music/update #13" {
        post("user/music/update", "[$uid, 2, [2235, 2, 10.5, 850311, [307, 94, 21, 24], 111, 6, 1, 0, 0, 0, 0, 0, 30, 81, 1], [[2, 0, 1008]]]").res exp
            "[[2235, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], 850311, 24, 0, 1, 0], [2235, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/sugoroku/update #11" {
        post("user/sugoroku/update", "[$uid, 23, 9, 220, 0, 0, [[5, 304130, 1]], 317, 0]").res exp
            "[]"
    }

    "user/music/update #14" {
        post("user/music/update", "[$uid, 3, [1068, 2, 11, 884696, [423, 106, 23, 23], 115, 6, 1, 0, 0, 0, 0, 0, 16, 113, 1], [[2, 0, 1008]]]").res exp
            "[[1068, 2, [1, 1, 0, 0, 0], [1, 1, 0, 0, 0], [0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], 884696, 23, 0, 1, 0], [1068, 1], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], []]"
    }

    "user/status/update #4" {
        post("user/status/update", "[$uid, 1, [[1, 0, 100], [2, 0, 250], [6, 102054, 1], [11, 105002, 1], [7, 301001, 3666], [7, 301002, 1100], [7, 301003, 366]], 0, 0, [], [1068, 2, 1, 7, 5]]").res exp
            "[]"
    }

    "user/rating/update #3" {
        post("user/rating/update", "[$uid, 1629, [[4, 3, 13], [1056, 3, 0], [1056, 2, 265], [2235, 2, 157], [1068, 2, 165]]]").res exp
            "[]"
    }
})