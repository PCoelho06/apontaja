import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import UiCheckbox from "./UiCheckbox.vue";

describe("UiCheckbox", () => {
  it("place la case dans son libellé", () => {
    const wrapper = mount(UiCheckbox, { props: { label: "Afficher les passées" } });

    const label = wrapper.find("label");
    expect(label.text()).toBe("Afficher les passées");
    expect(label.find('input[type="checkbox"]').exists()).toBe(true);
  });

  it("met à jour le v-model", async () => {
    const wrapper = mount(UiCheckbox, {
      props: { label: "Option", modelValue: false },
    });

    await wrapper.find("input").setValue(true);

    expect(wrapper.emitted("update:modelValue")).toEqual([[true]]);
  });

  it("reflète la valeur reçue et transmet les attributs natifs", () => {
    const wrapper = mount(UiCheckbox, {
      props: { label: "Option", modelValue: true },
      attrs: { disabled: true },
    });

    const input = wrapper.get<HTMLInputElement>("input");
    expect(input.element.checked).toBe(true);
    expect(input.attributes("disabled")).toBeDefined();
  });

  it("relie l'aide à la case", () => {
    const wrapper = mount(UiCheckbox, {
      props: { label: "Option", hint: "Visible seulement ici" },
    });

    const describedBy = wrapper.find("input").attributes("aria-describedby");
    expect(describedBy).toBeTruthy();
    expect(wrapper.find(`[id="${describedBy}"]`).text()).toBe("Visible seulement ici");
  });
});
