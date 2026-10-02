import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import { UiButton } from "@apontaja/ui-kit";

// Vérifie le câblage du workspace : résolution du paquet, compilation des .vue du ui-kit.
describe("ui-kit", () => {
  it("est résolu et rendu depuis portail-salon", () => {
    const wrapper = mount(UiButton, { slots: { default: "OK" } });

    expect(wrapper.text()).toBe("OK");
  });
});
