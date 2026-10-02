import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import UiButton from "./UiButton.vue";

describe("UiButton", () => {
  it("affiche son contenu et vaut type=button par défaut", () => {
    const wrapper = mount(UiButton, { slots: { default: "Enregistrer" } });

    expect(wrapper.text()).toBe("Enregistrer");
    expect(wrapper.attributes("type")).toBe("button");
  });

  it("transmet type=submit", () => {
    const wrapper = mount(UiButton, { props: { type: "submit" } });

    expect(wrapper.attributes("type")).toBe("submit");
  });

  it("est désactivé et occupé pendant le chargement", () => {
    const wrapper = mount(UiButton, { props: { loading: true } });

    expect(wrapper.attributes("disabled")).toBeDefined();
    expect(wrapper.attributes("aria-busy")).toBe("true");
  });

  it("déclenche le clic, sauf quand il est désactivé", async () => {
    const onClick = vi.fn();
    const enabled = mount(UiButton, { attrs: { onClick } });
    const disabled = mount(UiButton, {
      props: { disabled: true },
      attrs: { onClick },
    });

    await enabled.trigger("click");
    await disabled.trigger("click");

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("applique la classe de la variante", () => {
    const wrapper = mount(UiButton, { props: { variant: "danger" } });

    expect(wrapper.classes()).toContain("text-danger");
  });
});
