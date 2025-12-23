import { buildApplication, buildRouteMap } from "@stricli/core";
import { list } from "./commands/list";
import { info } from "./commands/info";
import { build } from "./commands/build";
import { testRoutes } from "./commands/test";
import { explore } from "./commands/explore";

const routes = buildRouteMap({
  routes: {
    list,
    info,
    build,
    test: testRoutes,
    explore,
  },
  docs: {
    brief: "Tachiyomi extension development CLI",
  },
});

export const app = buildApplication(routes, {
  name: "tachiyomi",
  versionInfo: {
    currentVersion: "0.1.0",
  },
});

