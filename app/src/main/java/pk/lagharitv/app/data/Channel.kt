package pk.lagharitv.app.data
data class Channel(
    val name: String, val url: String, val group: String? = null,
    val logo: String? = null, val tvgId: String? = null,
    val plot: String? = null, val rating: String? = null,
    val genre: String? = null, val releaseDate: String? = null,
    val duration: String? = null, val director: String? = null,
    val cast: String? = null
)
