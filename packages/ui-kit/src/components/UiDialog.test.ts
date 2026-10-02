import { mount } from "@vue/test-utils";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

import UiDialog from "./UiDialog.vue";

// jsdom n'implémente pas showModal()/close() : on simule leur effet sur l'attribut `open`.
const showModal = vi.fn(function (this: HTMLDialogElement) {
  this.setAttribute("open", "");
});
const close = vi.fn(function (this: HTMLDialogElement) {
  this.removeAttribute("open");
  this.dispatchEvent(new Event("close"));
});

beforeAll(() => {
  Object.defineProperty(HTMLDialogElement.prototype, "open", {
    configurable: true,
    get(this: HTMLDialogElement) {
      return this.hasAttribute("open");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    writable: true,
    value: showModal,
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    writable: true,
    value: close,
  });
});

beforeEach(() => {
  showModal.mockClear();
  close.mockClear();
});

function mountDialog(open: boolean) {
  const onUpdate = vi.fn();
  const wrapper = mount(UiDialog, {
    props: { open, title: "Nouveau rendez-vous", "onUpdate:open": onUpdate },
    slots: { default: "<p>Corps</p>", footer: "<button>OK</button>" },
  });
  return { wrapper, onUpdate };
}

describe("UiDialog", () => {
  it("s'ouvre en modal quand open est vrai et affiche titre, corps et pied", () => {
    const { wrapper } = mountDialog(true);

    expect(showModal).toHaveBeenCalledTimes(1);
    expect(wrapper.find("h2").text()).toBe("Nouveau rendez-vous");
    expect(wrapper.find("p").text()).toBe("Corps");
    expect(wrapper.find("footer").exists()).toBe(true);
    expect(wrapper.find("dialog").attributes("aria-labelledby")).toBe(
      wrapper.find("h2").attributes("id"),
    );
  });

  it("ne rend pas le corps tant qu'il est fermé", () => {
    const { wrapper } = mountDialog(false);

    expect(showModal).not.toHaveBeenCalled();
    expect(wrapper.find("p").exists()).toBe(false);
    expect(wrapper.find("footer").exists()).toBe(false);
  });

  it("se ferme quand open passe à faux", async () => {
    const { wrapper } = mountDialog(true);

    await wrapper.setProps({ open: false });

    expect(close).toHaveBeenCalledTimes(1);
  });

  it("répercute la fermeture native (Échap)", async () => {
    const { wrapper, onUpdate } = mountDialog(true);

    await wrapper.find("dialog").trigger("close");

    expect(onUpdate).toHaveBeenCalledWith(false);
  });

  it("se ferme au clic sur le fond mais pas sur le contenu", async () => {
    const { wrapper, onUpdate } = mountDialog(true);

    await wrapper.find("p").trigger("click");
    expect(onUpdate).not.toHaveBeenCalled();

    await wrapper.find("dialog").trigger("click");
    expect(onUpdate).toHaveBeenCalledWith(false);
  });

  it("se ferme avec le bouton Fermer", async () => {
    const { wrapper, onUpdate } = mountDialog(true);

    await wrapper.find('button[aria-label="Fermer"]').trigger("click");

    expect(onUpdate).toHaveBeenCalledWith(false);
  });
});
