#include "../../app/src/main/cpp/cache_policy.h"
#include <cassert>
#include <cstdint>
#include <iostream>
using Tokens = std::vector<int32_t>;
int main() {
    // A conversation continuation reuses the exact decoded prefix, including the assistant reply.
    assert(atlas::reusable_prefix(Tokens{1,2,3,4,5}, Tokens{1,2,3,4,5,6,7}, true) == 5);
    // Repeating a prompt still evaluates its final token to restore logits.
    assert(atlas::reusable_prefix(Tokens{1,2,3,4,5}, Tokens{1,2,3}, true) == 2);
    assert(atlas::reusable_prefix(Tokens{1,2,3}, Tokens{1,2,3}, true) == 2);
    // Editing, changing system instructions, switching conversation, empty caches and one-token prompts.
    assert(atlas::reusable_prefix(Tokens{1,2,3,4}, Tokens{1,9,3}, true) == 1);
    assert(atlas::reusable_prefix(Tokens{1,2}, Tokens{8,2}, true) == 0);
    assert(atlas::reusable_prefix(Tokens{1,2}, Tokens{1,2,3}, false) == 0);
    assert(atlas::reusable_prefix(Tokens{}, Tokens{1,2}, true) == 0);
    assert(atlas::reusable_prefix(Tokens{1}, Tokens{}, true) == 0);
    assert(atlas::reusable_prefix(Tokens{1}, Tokens{1}, true) == 0);
    assert((atlas::ContextShape{2048,128,32} == atlas::ContextShape{2048,128,32}));
    assert(!(atlas::ContextShape{2048,128,32} == atlas::ContextShape{1024,128,32}));
    assert(!(atlas::ContextShape{2048,128,32} == atlas::ContextShape{2048,256,32}));
    assert(!(atlas::ContextShape{2048,128,32} == atlas::ContextShape{2048,128,64}));
    std::cout << "Native cache policy: continuation, logits, edits, isolation and context shape passed\n";
}
