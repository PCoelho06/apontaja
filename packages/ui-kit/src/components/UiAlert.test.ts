import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import UiAlert from "./UiAlert.vue";

describe("UiAlert", () => {
  it("utilise role=alert pour une erreur", () => {
    const wrapper = mount(UiAlert, {
      props: { variant: "error" },
      slots: { default: "Échec" },
    });

    expect(wrapper.attributes("role")).toBe("alert");
    expect(wrapper.text()).toBe("Échec");
  });

  it("utilise role=status pour une information ou un succès", () => {
    expect(mount(UiAlert).attributes("role")).toBe("status");
    expect(
      mount(UiAlert, { props: { variant: "success" } }).attributes("role"),
    ).toBe("status");
  });
});
