package com.glimmercascade.game.level

enum class Biome { MEADOW, DUNES, FROSTPEAK, EMBERFALLS, TIDEPOOL }

enum class LevelObjective { SCORE_TARGET, CLEAR_BLOCKERS, COLLECT_COLOR }

data class LevelConfig(
    val id: Int,
    val biome: Biome,
    val gridWidth: Int,
    val gridHeight: Int,
    val moveLimit: Int?,
    val timeLimitSeconds: Int?,
    val objective: LevelObjective,
    val targetScore: Int,
    val blockerCount: Int,
    val star1Score: Int,
    val star2Score: Int,
    val star3Score: Int
)

private fun roundTo10(n: Int): Int = ((n + 5) / 10) * 10

object LevelRepository {

    val handAuthoredLevels: List<LevelConfig> = listOf(
        LevelConfig(
            id = 1,
            biome = Biome.MEADOW,
            gridWidth = 7,
            gridHeight = 7,
            moveLimit = 25,
            timeLimitSeconds = null,
            objective = LevelObjective.SCORE_TARGET,
            targetScore = 300,
            blockerCount = 0,
            star1Score = 300,
            star2Score = roundTo10((300 * 1.3).toInt()),
            star3Score = roundTo10((300 * 1.6).toInt())
        ),
        LevelConfig(
            id = 2,
            biome = Biome.MEADOW,
            gridWidth = 7,
            gridHeight = 7,
            moveLimit = 24,
            timeLimitSeconds = null,
            objective = LevelObjective.SCORE_TARGET,
            targetScore = 450,
            blockerCount = 0,
            star1Score = 450,
            star2Score = roundTo10((450 * 1.3).toInt()),
            star3Score = roundTo10((450 * 1.6).toInt())
        ),
        LevelConfig(
            id = 3,
            biome = Biome.MEADOW,
            gridWidth = 6,
            gridHeight = 6,
            moveLimit = 23,
            timeLimitSeconds = null,
            objective = LevelObjective.SCORE_TARGET,
            targetScore = 600,
            blockerCount = 0,
            star1Score = 600,
            star2Score = roundTo10((600 * 1.3).toInt()),
            star3Score = roundTo10((600 * 1.6).toInt())
        ),
        LevelConfig(
            id = 4,
            biome = Biome.MEADOW,
            gridWidth = 6,
            gridHeight = 6,
            moveLimit = 22,
            timeLimitSeconds = null,
            objective = LevelObjective.SCORE_TARGET,
            targetScore = 750,
            blockerCount = 0,
            star1Score = 750,
            star2Score = roundTo10((750 * 1.3).toInt()),
            star3Score = roundTo10((750 * 1.6).toInt())
        ),
        LevelConfig(
            id = 5,
            biome = Biome.MEADOW,
            gridWidth = 6,
            gridHeight = 6,
            moveLimit = 21,
            timeLimitSeconds = null,
            objective = LevelObjective.SCORE_TARGET,
            targetScore = 900,
            blockerCount = 0,
            star1Score = 900,
            star2Score = roundTo10((900 * 1.3).toInt()),
            star3Score = roundTo10((900 * 1.6).toInt())
        )
    )

    fun generateLevel(id: Int): LevelConfig {
        val biome = when (id) {
            in 1..20 -> Biome.MEADOW
            in 21..40 -> Biome.DUNES
            in 41..60 -> Biome.FROSTPEAK
            in 61..80 -> Biome.EMBERFALLS
            else -> Biome.TIDEPOOL
        }

        val gridDimension = (7 + (id / 15)).coerceAtMost(9)

        val objectives = listOf(
            LevelObjective.SCORE_TARGET,
            LevelObjective.SCORE_TARGET,
            LevelObjective.CLEAR_BLOCKERS,
            LevelObjective.SCORE_TARGET,
            LevelObjective.COLLECT_COLOR
        )
        val objective = objectives[id % 5]

        val blockerCount = if (id <= 10) 0 else ((id - 10) / 9).coerceAtMost(10)

        val isTimeLimited = id >= 15 && id % 4 == 0
        val moveLimit = if (isTimeLimited) null else (30 - id / 5).coerceAtLeast(12)
        val timeLimitSeconds = if (isTimeLimited) (90 + id) else null

        val rawTarget = 500 + id * 85
        val targetScore = (rawTarget / 50) * 50

        val star1 = targetScore
        val star2 = roundTo10((targetScore * 1.3).toInt())
        val star3 = roundTo10((targetScore * 1.6).toInt())

        return LevelConfig(
            id = id,
            biome = biome,
            gridWidth = gridDimension,
            gridHeight = gridDimension,
            moveLimit = moveLimit,
            timeLimitSeconds = timeLimitSeconds,
            objective = objective,
            targetScore = targetScore,
            blockerCount = blockerCount,
            star1Score = star1,
            star2Score = star2,
            star3Score = star3
        )
    }

    val allLevels: List<LevelConfig> =
        (handAuthoredLevels + (6..100).map(::generateLevel)).sortedBy { it.id }

    fun byId(id: Int): LevelConfig? = allLevels.find { it.id == id }
}