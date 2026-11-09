package pk.lagharitv.app.data

object M3uParser {
    fun parse(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = "Unknown"
        var group: String? = null
        var logo: String? = null
        var tvgId: String? = null
        for (line in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(",", "Unknown").trim()
                group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                tvgId = Regex("tvg-id=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
            } else if (!line.startsWith("#") &&
                (line.startsWith("https://") || line.startsWith("http://"))) {
                result += Channel(name, line, group, logo, tvgId)
                name = "Unknown"; group = null; logo = null; tvgId = null
            }
        }
        return result
    }
}
