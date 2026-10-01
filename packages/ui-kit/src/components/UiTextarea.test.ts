import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import UiTextarea from "./UiTextarea.vue";

describe("UiTextarea", () => {
  it("met à jour le v-model et affiche l'erreur", async () => {
    const wrapper = mount(UiTextarea, {
      props: { label: "Notes", error: "Trop long" },
    });

    await wrapper.find("textarea").setValue("bonjour");

    expect(wrapper.emitted("update:modelValue")).toEqual([["bonjour"]]);
    expect(wrapper.find("textarea").attributes("aria-invalid")).toBe("true");
    expect(wrapper.text()).toContain("Trop long");
  });

  it("vaut 3 lignes par défaut, modifiable", () => {
    const byDefault = mount(UiTextarea, { props: { label: "Notes" } });
    const custom = mount(UiTextarea, {
      props: { label: "Notes" },
      attrs: { rows: 6 },
    });

    expect(byDefault.find("textarea").attributes("rows")).toBe("3");
    expect(custom.find("textarea").attributes("rows")).toBe("6");
  });
});
