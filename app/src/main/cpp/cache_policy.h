#pragma once
#include <algorithm>
#include <cstddef>
#include <vector>

namespace atlas {
// The final prompt token is always evaluated again: KV alone does not restore logits.
template<class T> size_t reusable_prefix(const std::vector<T> &cached, const std::vector<T> &prompt, bool same_session) {
    if (!same_session || prompt.empty()) return 0;
    size_t n = 0;
    while (n < cached.size() && n < prompt.size() && cached[n] == prompt[n]) ++n;
    return std::min(n, prompt.size() - 1);
}
struct ContextShape {
    int context = 0, batch = 0, microbatch = 0;
    bool operator==(const ContextShape &o) const { return context == o.context && batch == o.batch && microbatch == o.microbatch; }
};
}
