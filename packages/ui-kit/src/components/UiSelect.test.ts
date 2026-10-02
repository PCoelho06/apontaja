import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import UiSelect from "./UiSelect.vue";

const OPTIONS = '<option value="a">A</option><option value="b">B</option>';

describe("UiSelect", () => {
  it("rend les options fournies et associe le libellé", () => {
    const wrapper = mount(UiSelect, {
      props: { label: "Rôle" },
      slots: { default: OPTIONS },
    });

    expect(wrapper.findAll("option")).toHaveLength(2);
    expect(wrapper.find("label").attributes("for")).toBe(
      wrapper.find("select").attributes("id"),
    );
  });

  it("met à jour le v-model", async () => {
    const wrapper = mount(UiSelect, {
      props: { label: "Rôle", modelValue: "a" },
      slots: { default: OPTIONS },
    });

    await wrapper.find("select").setValue("b");

    expect(wrapper.emitted("update:modelValue")).toEqual([["b"]]);
  });
});
