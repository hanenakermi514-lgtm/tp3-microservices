package org.example.calculservice.controller;

import org.example.calculservice.model.Resultat;
import org.example.calculservice.service.Calculatrice;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/calcul")
public class CalculController {
    private final Calculatrice calculService;
    // Injection par constructeur : Spring fournit l'objet Calculatrice
    public CalculController(Calculatrice calculService) {
        this.calculService = calculService;
    }
    @GetMapping(value = "/somme", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> somme(@RequestParam("a") int a,
                                        @RequestParam("b") int b) {
        int result = calculService.somme(a, b);
        return ResponseEntity.ok(String.valueOf(result));
    }
    @GetMapping(value = "/soustraction", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> soustraction(@RequestParam("a") int a,
                                               @RequestParam("b") int b) {
        return ResponseEntity.ok(String.valueOf(calculService.soustraction(a, b)));
    }

    @GetMapping(value = "/produit", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> produit(@RequestParam("a") int a,
                                          @RequestParam("b") int b) {
        return ResponseEntity.ok(String.valueOf(calculService.produit(a, b)));
    }
    @GetMapping(value = "/division", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> division(@RequestParam("a") int a,
                                           @RequestParam("b") int b) {
        if (b == 0) {
            return ResponseEntity.badRequest()
                    .body("Erreur : division par 0 impossible");
        }
        double result = calculService.division(a, b);
        return ResponseEntity.ok(String.valueOf(result));
    }
    @GetMapping("/somme/detail")
    public Resultat sommeDetail(@RequestParam("a") int a,
                                @RequestParam("b") int b) {
        return new Resultat("somme", a, b, calculService.somme(a, b));
    }
    @GetMapping("/division/detail")
    public ResponseEntity<Resultat> divisionDetail(@RequestParam("a") int a,
                                                   @RequestParam("b") int b) {
        if (b == 0) {
            return ResponseEntity.badRequest().build();
        }
        Resultat resultat = new Resultat("division", a, b, calculService.division(a, b));
        return ResponseEntity.ok(resultat);
    }
}
