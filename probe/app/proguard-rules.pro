# 探针不需要额外 keep 规则：Compose 与 Activity 的 keep 由 R8 默认规则覆盖。
# 若 release 构建出现运行时问题，优先怀疑反射/序列化，而不是 Compose。
