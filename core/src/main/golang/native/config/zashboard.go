package config

import (
	"sync"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/log"
)

// Debug → Zashboard: the controller the dashboard in the app talks to. While
// it is set, it is the core's only controller — whatever the profile or
// Override says about external-controller, its TLS, unix and pipe variants,
// CORS and the secret is replaced.
var (
	zashboardMutex      sync.Mutex
	zashboardController string
	zashboardSecret     string
)

// SetZashboardController sets the controller for the next load; an empty
// address leaves the controller to the profile and Override again.
func SetZashboardController(addr, secret string) {
	zashboardMutex.Lock()
	defer zashboardMutex.Unlock()

	zashboardController = addr
	zashboardSecret = secret
}

func patchZashboard(cfg *config.RawConfig, _ string) error {
	zashboardMutex.Lock()
	addr, secret := zashboardController, zashboardSecret
	zashboardMutex.Unlock()

	if addr == "" {
		return nil
	}

	cfg.ExternalController = addr
	cfg.ExternalControllerTLS = ""
	cfg.ExternalControllerUnix = ""
	cfg.ExternalControllerPipe = ""
	cfg.Secret = secret
	// The page is served from the app's own origin: any origin, as mihomo's default.
	cfg.ExternalControllerCors = config.RawCors{
		AllowOrigins:        []string{"*"},
		AllowPrivateNetwork: true,
	}

	log.Infoln("Zashboard: controller on %s", addr)

	return nil
}
