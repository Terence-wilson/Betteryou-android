package com.betteryou.app.domain

/** The four kinds of lists a user maintains. */
enum class ItemKind(val title: String) {
    HOBBY("Hobbies & Interests"),
    GOOD_HABIT("Good Habits"),
    BAD_HABIT("Bad Habits"),
    BREAK_ACTIVITY("Breaks");

    /** Maximum number of items that can be focused on per cycle. `null` means unlimited. */
    val focusLimit: Int?
        get() = if (this == BREAK_ACTIVITY) null else 3

    val placeholder: String
        get() = when (this) {
            HOBBY -> "Add a hobby, e.g. Guitar"
            GOOD_HABIT -> "Add a good habit, e.g. Drink water"
            BAD_HABIT -> "Add a bad habit, e.g. Late-night snacking"
            BREAK_ACTIVITY -> "Add a break, e.g. Social media"
        }

    val explanation: String
        get() = when (this) {
            HOBBY -> "Things you want to develop. Focus on up to 3 per cycle and rate your consistency, enjoyment and improvement at each check-in."
            GOOD_HABIT -> "Habits to build. During a reset, do them every day."
            BAD_HABIT -> "Habits to control. During a reset, stop them entirely."
            BREAK_ACTIVITY -> "Things you want a break from, like social media. During a reset, avoid them completely."
        }

    val suggestions: List<String>
        get() = when (this) {
            HOBBY -> listOf("Reading", "Guitar", "Drawing", "Cooking", "Running", "Photography", "Learning a language", "Coding")
            GOOD_HABIT -> listOf("Drink water", "Exercise", "Meditate", "Journal", "Sleep by 11pm", "Stretch", "Walk 10k steps")
            BAD_HABIT -> listOf("Smoking", "Junk food", "Nail biting", "Late-night snacking", "Procrastinating", "Doomscrolling")
            BREAK_ACTIVITY -> listOf("Social media", "Alcohol", "Caffeine", "Video games", "Streaming TV", "Online shopping")
        }

    fun goal(inReset: Boolean): String = when (this) {
        HOBBY -> "Practice regularly and notice how much you enjoy it."
        GOOD_HABIT -> if (inReset) "Do these every day during your reset." else "Keep them going until your check-in."
        BAD_HABIT -> if (inReset) "Stop these completely during your reset." else "Keep your distance until your check-in."
        BREAK_ACTIVITY -> if (inReset) "Take a full break during your reset." else "Use these mindfully until your check-in."
    }

    /** Question shown when rating how well a habit was followed after the reset. */
    val adherenceQuestion: String
        get() = when (this) {
            HOBBY, GOOD_HABIT -> "How consistently did you keep it up?"
            BAD_HABIT -> "How well did you stay away from it?"
            BREAK_ACTIVITY -> "How well did you stick to your break?"
        }

    val adherenceLowLabel: String
        get() = when (this) {
            HOBBY, GOOD_HABIT -> "Rarely"
            BAD_HABIT, BREAK_ACTIVITY -> "Not at all"
        }

    val adherenceHighLabel: String
        get() = when (this) {
            HOBBY, GOOD_HABIT -> "Every day"
            BAD_HABIT, BREAK_ACTIVITY -> "Completely"
        }

    companion object {
        /** Kinds rated on adherence at a check-in. */
        val habitKinds = listOf(GOOD_HABIT, BAD_HABIT, BREAK_ACTIVITY)

        fun fromStorage(value: String): ItemKind = entries.firstOrNull { it.name == value } ?: HOBBY
    }
}
