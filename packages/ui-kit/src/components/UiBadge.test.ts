import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import UiBadge from "./UiBadge.vue";

describe("UiBadge", () => {
  it("affiche son contenu avec la teinte demandée", () => {
    const wrapper = mount(UiBadge, {
      props: { tone: "wine" },
      slots: { default: "Confirmé" },
    });

    expect(wrapper.text()).toBe("Confirmé");
    expect(wrapper.classes()).toContain("text-wine");
  });
});
