package descriptions

import (
	"strings"
	"testing"
)

func TestBuildDescriptions(t *testing.T) {
	proxies := []map[string]any{
		{"name": "primary", "serverDescription": "  Primary text  ", "server_description": "x", "description": "y"},
		{"name": "snake", "server_description": "Snake text", "description": "y"},
		{"name": "kebab", "server-description": "Kebab text"},
		{"name": "fallback", "description": "Fallback text"},
		{"name": "blank-primary", "serverDescription": "   ", "description": "Used after blank"},
		{"name": "none"},
		{"name": "", "description": "no name"},
		{"name": "not-a-string", "description": 5},
	}
	groups := []map[string]any{
		{"name": "👻 Prizrak", "description": "Auto-pick the best location", "serverDescription": "ignored"},
		{"name": "blank", "description": "  "},
		{"name": "none"},
	}

	got := BuildDescriptions(proxies, groups)
	want := map[string]string{
		"primary":       "Primary text",
		"snake":         "Snake text",
		"kebab":         "Kebab text",
		"fallback":      "Fallback text",
		"blank-primary": "Used after blank",
		"👻 Prizrak":     "Auto-pick the best location",
	}

	if len(got) != len(want) {
		t.Fatalf("got %v, want %v", got, want)
	}
	for name, desc := range want {
		if got[name] != desc {
			t.Errorf("description of %q = %q, want %q", name, got[name], desc)
		}
	}
}

func TestBuildDescriptionsNoLengthLimit(t *testing.T) {
	long := strings.Repeat("я", 100)
	got := BuildDescriptions(nil, []map[string]any{{"name": "g", "description": long}})
	if got["g"] != long {
		t.Errorf("long description was altered: %q", got["g"])
	}
}

func TestApplyAndGet(t *testing.T) {
	Apply([]map[string]any{{"name": "a", "serverDescription": "A"}}, nil)
	if Get("a") != "A" || Get("missing") != "" {
		t.Fatalf("unexpected lookup: %q %q", Get("a"), Get("missing"))
	}

	Apply(nil, nil)
	if Get("a") != "" {
		t.Errorf("descriptions must be replaced, got %q", Get("a"))
	}
}
