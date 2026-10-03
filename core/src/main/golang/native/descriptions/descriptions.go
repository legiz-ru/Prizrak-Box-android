package descriptions

import (
	"strings"
	"sync"
)

// proxyDescriptionKeys are the keys a proxy description is read from, in
// priority order: serverDescription is the one the panel writes, the rest are
// fallbacks.
var proxyDescriptionKeys = []string{
	"serverDescription",
	"server_description",
	"server-description",
	"description",
}

// groupDescriptionKey is the only key a proxy-groups description is read from.
const groupDescriptionKey = "description"

var (
	descriptionsMu sync.RWMutex
	descriptions   = map[string]string{}
)

// BuildDescriptions collects name -> description from the raw `proxies` and
// `proxy-groups` blocks of a profile. Proxy and group names are unique in
// mihomo, so one map serves both. Blank values are skipped; the length is not
// limited.
func BuildDescriptions(proxies, groups []map[string]any) map[string]string {
	result := map[string]string{}

	collectDescriptions(proxies, proxyDescriptionKeys, result)
	collectDescriptions(groups, []string{groupDescriptionKey}, result)

	return result
}

func collectDescriptions(items []map[string]any, keys []string, out map[string]string) {
	for _, item := range items {
		name, ok := item["name"].(string)
		if !ok || name == "" {
			continue
		}

		for _, key := range keys {
			value, ok := item[key].(string)
			if !ok {
				continue
			}

			if value = strings.TrimSpace(value); value != "" {
				out[name] = value

				break
			}
		}
	}
}

// Apply replaces the descriptions of the loaded profile.
func Apply(proxies, groups []map[string]any) {
	built := BuildDescriptions(proxies, groups)

	descriptionsMu.Lock()
	descriptions = built
	descriptionsMu.Unlock()
}

// Get returns the description of a proxy or group, or "" if it has none.
func Get(name string) string {
	descriptionsMu.RLock()
	defer descriptionsMu.RUnlock()

	return descriptions[name]
}
