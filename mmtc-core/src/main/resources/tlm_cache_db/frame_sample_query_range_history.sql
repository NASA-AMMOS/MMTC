CREATE TABLE "frame_sample_query_range_history" (
    id INTEGER PRIMARY KEY,
    "startSec" NUMERIC NOT NULL,
    "startNanoOfSec" NUMERIC NOT NULL,
    "stopSec" NUMERIC NOT NULL,
    "stopNanoOfSec" NUMERIC NOT NULL
)