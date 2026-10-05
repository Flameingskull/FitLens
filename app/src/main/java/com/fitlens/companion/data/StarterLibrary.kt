package com.fitlens.companion.data

import android.content.res.Resources
import com.fitlens.companion.R


/** One exercise in the starter library. [type] uses [ExerciseTypes]. */
data class StarterExercise(val name: String, val type: Int = ExerciseTypes.WEIGHT_REPS)

/** One category in the starter library, with the exercises filed under it. */
data class StarterCategory(val name: String, val exercises: List<StarterExercise>)

/**
 * A small, opinionated starting point for someone who has no FitNotes backup to import: common categories with a
 * handful of well-known exercises each.
 *
 * It is **never** seeded automatically. The user asks for it from the exercise library (or, later, from first-run
 * setup), and [Workouts.seedStarterLibrary] only ever **adds** names that aren't already there, so it can't wipe
 * or rename anything the user logged or imported.
 */
object StarterLibrary {

    val categories: List<StarterCategory> = listOf(
        StarterCategory(
            "Chest",
            listOf(
                StarterExercise("Barbell Bench Press"),
                StarterExercise("Incline Barbell Bench Press"),
                StarterExercise("Dumbbell Bench Press"),
                StarterExercise("Incline Dumbbell Bench Press"),
                StarterExercise("Chest Fly"),
                StarterExercise("Cable Crossover"),
                StarterExercise("Push Up"),
                StarterExercise("Chest Dip")
            )
        ),
        StarterCategory(
            "Back",
            listOf(
                StarterExercise("Deadlift"),
                StarterExercise("Barbell Row"),
                StarterExercise("Dumbbell Row"),
                StarterExercise("Pull Up"),
                StarterExercise("Chin Up"),
                StarterExercise("Lat Pulldown"),
                StarterExercise("Seated Cable Row"),
                StarterExercise("Face Pull")
            )
        ),
        StarterCategory(
            "Shoulders",
            listOf(
                StarterExercise("Overhead Press"),
                StarterExercise("Seated Dumbbell Press"),
                StarterExercise("Arnold Press"),
                StarterExercise("Lateral Raise"),
                StarterExercise("Front Raise"),
                StarterExercise("Rear Delt Fly"),
                StarterExercise("Upright Row"),
                StarterExercise("Shrug")
            )
        ),
        StarterCategory(
            "Legs",
            listOf(
                StarterExercise("Back Squat"),
                StarterExercise("Front Squat"),
                StarterExercise("Romanian Deadlift"),
                StarterExercise("Leg Press"),
                StarterExercise("Lunge"),
                StarterExercise("Bulgarian Split Squat"),
                StarterExercise("Leg Extension"),
                StarterExercise("Lying Leg Curl"),
                StarterExercise("Standing Calf Raise"),
                StarterExercise("Hip Thrust")
            )
        ),
        StarterCategory(
            "Arms",
            listOf(
                StarterExercise("Barbell Curl"),
                StarterExercise("Dumbbell Curl"),
                StarterExercise("Hammer Curl"),
                StarterExercise("Preacher Curl"),
                StarterExercise("Close Grip Bench Press"),
                StarterExercise("Triceps Pushdown"),
                StarterExercise("Overhead Triceps Extension"),
                StarterExercise("Triceps Dip")
            )
        ),
        StarterCategory(
            "Core",
            listOf(
                StarterExercise("Plank", ExerciseTypes.TIME),
                StarterExercise("Side Plank", ExerciseTypes.TIME),
                StarterExercise("Hanging Leg Raise"),
                StarterExercise("Cable Crunch"),
                StarterExercise("Ab Wheel Rollout"),
                StarterExercise("Russian Twist"),
                StarterExercise("Back Extension")
            )
        ),
        StarterCategory(
            "Cardio",
            listOf(
                StarterExercise("Treadmill Run", ExerciseTypes.DISTANCE_TIME),
                StarterExercise("Outdoor Run", ExerciseTypes.DISTANCE_TIME),
                StarterExercise("Cycling", ExerciseTypes.DISTANCE_TIME),
                StarterExercise("Rowing Machine", ExerciseTypes.DISTANCE_TIME),
                StarterExercise("Elliptical", ExerciseTypes.DISTANCE_TIME),
                StarterExercise("Stair Climber", ExerciseTypes.TIME),
                StarterExercise("Skipping", ExerciseTypes.TIME)
            )
        )
    )

    val exerciseCount: Int get() = categories.sumOf { it.exercises.size }
}

/** What [Workouts.seedStarterLibrary] added. Nothing that already existed is ever changed. */
data class SeedResult(val categoriesAdded: Int, val exercisesAdded: Int, val skipped: Int) {
    /** "Added 12 exercises and 3 categories. The 4 you already had were left as they are." (#156) */
    fun text(res: Resources): String {
        if (categoriesAdded == 0 && exercisesAdded == 0) return res.getString(R.string.seed_nothing)
        val exercises = res.getQuantityString(R.plurals.seed_exercises, exercisesAdded, exercisesAdded)
        val categories = res.getQuantityString(R.plurals.seed_categories, categoriesAdded, categoriesAdded)
        val what = when {
            exercisesAdded > 0 && categoriesAdded > 0 -> res.getString(R.string.seed_both, exercises, categories)
            exercisesAdded > 0 -> exercises
            else -> categories
        }
        val added = res.getString(R.string.seed_added, what)
        return if (skipped > 0) res.getString(R.string.seed_added_kept, added, res.getQuantityString(R.plurals.seed_kept, skipped, skipped))
        else added
    }
}

