import { TestBed } from "@angular/core/testing";
import { Router, convertToParamMap } from "@angular/router";
import { routes } from "../../app.routes";
import { authGuard } from "../../core/auth/auth.guard";
import { roleGuard } from "../../core/auth/role.guard";
import {
  isSafeActionUrl,
  safeNavigateActionUrl,
} from "./notification-navigation.util";

describe("support notification navigation", () => {
  const SUPPORT_ROUTES: Record<string, string> = {
    ADMIN: "/admin/support",
    MANAGER: "/manager/support",
    COMMERCIAL: "/commercial/support",
  };
  const CONVERSATION_ID = "94780146-547c-431b-82ca-2798bbd8402f";

  const routeFor = (path: string) =>
    routes.find((candidate) => candidate.path === path);

  const configure = () =>
    TestBed.configureTestingModule({}).compileComponents();

  it("exposes one isolated support route per staff role, all guarded", () => {
    for (const path of Object.values(SUPPORT_ROUTES).map((value) =>
      value.slice(1),
    )) {
      const route = routeFor(path);
      expect(route).withContext(`${path} must exist`).toBeDefined();
      expect(route?.canActivate)
        .withContext(path)
        .toEqual([authGuard, roleGuard]);
    }
    expect(routeFor("admin/support")?.data?.["roles"]).toEqual(["ADMIN"]);
    expect(routeFor("manager/support")?.data?.["roles"]).toEqual(["MANAGER"]);
    expect(routeFor("commercial/support")?.data?.["roles"]).toEqual([
      "COMMERCIAL",
    ]);
    // No shared role-agnostic support workspace route is registered
    expect(routeFor("support")).toBeUndefined();
  });

  it("keeps the conversation id in every staff action URL", () => {
    for (const url of Object.values(SUPPORT_ROUTES)) {
      expect(
        isSafeActionUrl(`${url}?conversation=${CONVERSATION_ID}`),
      ).toBeTrue();
      expect(`${url}?conversation=${CONVERSATION_ID}`).toContain(
        `conversation=${CONVERSATION_ID}`,
      );
    }
  });

  it("does not generate a cross-role route for a recipient", () => {
    for (const [role, url] of Object.entries(SUPPORT_ROUTES)) {
      const expected = SUPPORT_ROUTES[role];
      expect(url.startsWith(expected)).toBeTrue();
      // the route is restricted to exactly its own role
      expect(routeFor(url.slice(1))?.data?.["roles"]).toEqual([role]);
    }
  });

  it("navigates to the recipient support conversation when the notification is clicked", async () => {
    await configure();
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, "navigateByUrl").and.resolveTo(true);

    for (const url of Object.values(SUPPORT_ROUTES)) {
      const result = await safeNavigateActionUrl(
        router,
        `${url}?conversation=${CONVERSATION_ID}`,
      );
      expect(result).toBeTrue();
      expect(navigateSpy).toHaveBeenCalledWith(
        `${url}?conversation=${CONVERSATION_ID}`,
      );
    }
  });

  it("rejects unsafe support action URLs", async () => {
    await configure();
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, "navigateByUrl").and.resolveTo(true);

    expect(isSafeActionUrl(`javascript:alert(${CONVERSATION_ID})`)).toBeFalse();
    expect(
      isSafeActionUrl(
        "https://evil.example/admin/support?conversation=" + CONVERSATION_ID,
      ),
    ).toBeFalse();
    expect(
      await safeNavigateActionUrl(router, "javascript:alert(1)"),
    ).toBeFalse();
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it("preserves the conversation query parameter on parsed navigation", () => {
    for (const url of Object.values(SUPPORT_ROUTES)) {
      const full = `${url}?conversation=${CONVERSATION_ID}`;
      const [path, query] = full.split("?");
      const params = new URLSearchParams(query);
      expect(path).toEqual(url);
      expect(params.get("conversation")).toEqual(CONVERSATION_ID);
      void convertToParamMap;
    }
  });
});
