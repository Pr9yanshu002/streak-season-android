package dev.priyanshu.arc.domain.model

enum class ArcStatus { DRAFT, ACTIVE, COMPLETED, FAILED, ARCHIVED }

enum class Strictness { STRICT, STANDARD, CHILL }

enum class CommitmentType { BINARY, COUNTER, DURATION, LOG }

enum class ScheduleType { DAILY, WEEKDAYS }

// R5. out_of_range is a real state (dates outside the arc's range) but is
// never rendered by the UI.
enum class DayState { FUTURE, OUT_OF_RANGE, COMPLETE, PENDING, REST, MISSED, PARTIAL }

// R3/R4, per commitment per date. Rest tokens (R6) never appear here — they
// only ever change the aggregate DayState, not an individual commitment's
// satisfaction.
enum class CommitmentStatus { NOT_SCHEDULED, PENDING, DONE }

enum class MilestoneKind { START, P25, P50, P75, FINAL }
