import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import UiButton from "./UiButton.vue";

describe("UiButton, taille", () => {
  it("est de taille normale par défaut", () => {
    const wrapper = mount(UiButton);

    expect(wrapper.classes()).toContain("px-4");
    expect(wrapper.classes()).toContain("text-sm");
  });

  it("propose une taille compacte pour les lignes de liste", () => {
    const wrapper = mount(UiButton, { props: { size: "sm" } });

    expect(wrapper.classes()).toContain("px-2.5");
    expect(wrapper.classes()).toContain("text-xs");
    expect(wrapper.classes()).not.toContain("px-4");
  });
});
