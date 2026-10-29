package jp.passgen.core

/** Public teaching fixtures, never generated/user secrets. Do not offer these as passwords. */
object SecurityGuideExamples {
    const val NOTICE = "すべて公開の説明用例です。実際のパスワードには使わないでください。"
    const val PASSWORD = "ak5SU-KYn3!hB&77=S#HnumX"
    const val USERNAME = "demo-user"
    const val SERVICE = "shop.example.com"
    const val CODE = "482915"
    const val EXPECTED_URL = "https://account.example.com"
    const val LOOKALIKE_URL = "https://account.example.com.login-check.example.net"
    val weakExamples = listOf(
        WeakPasswordExample("word", "よくある単語", "password", "単語そのものは、攻撃で最初に試されやすい候補です。"),
        WeakPasswordExample("sequence", "連番", "12345678", "文字数があっても、並びが決まっています。"),
        WeakPasswordExample("keyboard", "キーボードの順", "qwertyuiop", "指でなぞった並びも、推測の候補になります。"),
        WeakPasswordExample("personal", "名前と誕生日", "Taro19900401", "架空の名前と日付の例。公開情報から組み立てられる値は避けます。"),
        WeakPasswordExample("phrase", "よくあるフレーズ", "iloveyouforever", "長く見えても、知られた言い回しをそのまま使っています。"),
        WeakPasswordExample("repeat", "同じ文字の繰り返し", "aaaaaaaaaaaa", "同じ文字を増やすだけでは、推測しにくくなりません。")
    )
    val mutations = listOf("password", "Password1!", "P@ssw0rd1!")
    val numericExamples = listOf(
        NumericPasswordExample("4827", 10000, "1万通り"),
        NumericPasswordExample("604839", 1000000, "100万通り"),
        NumericPasswordExample("92740631", 100000000, "1億通り")
    )
    val phraseWords = listOf("laborer", "chihuahua", "revolving", "palm", "shout", "oink", "pyromania")
    fun phrase(separator: String): String {
        require(separator in listOf(" ", "-", "_"))
        return phraseWords.joinToString(separator)
    }
}

class WeakPasswordExample(val key: String, val title: String, val value: String, val reason: String)
class NumericPasswordExample(val value: String, val candidateCount: Long, val candidateLabel: String) {
    val length: Int get() = value.length
}
