import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import UiInput from "./UiInput.vue";

describe("UiInput", () => {
  it("associe le libellé au champ", () => {
    const wrapper = mount(UiInput, { props: { label: "Email" } });

    const id = wrapper.find("input").attributes("id");
    expect(id).toBeTruthy();
    expect(wrapper.find("label").attributes("for")).toBe(id);
    expect(wrapper.find("label").text()).toBe("Email");
  });

  it("met à jour le v-model", async () => {
    const wrapper = mount(UiInput, { props: { label: "Email" } });

    await wrapper.find("input").setValue("a@b.fr");

    expect(wrapper.emitted("update:modelValue")).toEqual([["a@b.fr"]]);
  });

  it("signale l'erreur de façon accessible", () => {
    const wrapper = mount(UiInput, {
      props: { label: "Email", error: "Adresse invalide" },
    });

    const input = wrapper.find("input");
    expect(input.attributes("aria-invalid")).toBe("true");
    const describedBy = input.attributes("aria-describedby");
    expect(describedBy).toBeTruthy();
    expect(wrapper.find(`[id="${describedBy}"]`).text()).toBe(
      "Adresse invalide",
    );
  });

  it("n'est pas marqué invalide sans erreur et affiche l'aide", () => {
    const wrapper = mount(UiInput, {
      props: { label: "Email", hint: "Utilisé pour la connexion" },
    });

    expect(wrapper.find("input").attributes("aria-invalid")).toBeUndefined();
    expect(wrapper.text()).toContain("Utilisé pour la connexion");
  });

  it("transmet les attributs natifs au champ", () => {
    const wrapper = mount(UiInput, {
      props: { label: "Email", type: "email" },
      attrs: { placeholder: "vous@exemple.fr", required: true },
    });

    const input = wrapper.find("input");
    expect(input.attributes("type")).toBe("email");
    expect(input.attributes("placeholder")).toBe("vous@exemple.fr");
    expect(input.attributes("required")).toBeDefined();
  });
});
