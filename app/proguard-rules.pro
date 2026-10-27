# Rules are added with a comment explaining why each one is needed.

# SignalR logs through slf4j-api, which looks up an optional binding at runtime and falls back to
# a no-op logger when none is bundled. We ship no binding, so the class is absent by design.
-dontwarn org.slf4j.impl.StaticLoggerBinder
