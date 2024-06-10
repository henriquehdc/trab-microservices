package trabalho1.microservices;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/usuario")
public class Usuario {

  @Autowired
  private UsuarioDAO dao;

  @PostMapping
  public ResponseEntity<String> createUser(@RequestBody UsuarioBean user) throws DupedIdException {

    if (dao.count() == 0) {
      System.out.println("Nenhum usuario cadastrado");
      dao.save(user);
      return new ResponseEntity<String>(user.getUsername(), HttpStatus.CREATED);
    } else if (dao.findByUsername(user.getUsername()) != null) {
      System.out.println("Usuario já existe!");
      return new ResponseEntity<String>("Usuario ja existe", HttpStatus.NOT_ACCEPTABLE);
    }
    System.out.println("Usuário não existe");
    user.setTotalFails(0);
    user.setTotalLogins(0);
    System.out.println(user);
    dao.save(user);
    return new ResponseEntity<String>(user.getUsername(), HttpStatus.CREATED);

  }

  @GetMapping()
  public ResponseEntity<Iterable<UsuarioBean>> allUsers() {
    System.out.println(dao.count());

    if (dao.count() > 0) {
      return new ResponseEntity<Iterable<UsuarioBean>>(dao.findAll(), HttpStatus.OK);
    } else {
      System.out.println("Não existem usuários");
      return new ResponseEntity<Iterable<UsuarioBean>>(HttpStatus.NO_CONTENT);
    }
  }

  @PutMapping()
  public ResponseEntity<String> updateUser(@RequestBody UsuarioBean user) {
    if (user.getUsername() == null || user.getPassword() == null) {
      return new ResponseEntity<String>("Usuário e senha não podem ser nulos", HttpStatus.BAD_REQUEST);
    }

    UsuarioBean userExists = dao.findByUsername(user.getUsername());
    if (userExists == null) {
      System.out.println("Nenhum usuário encontrado");
      return new ResponseEntity<String>("Nenhum usuário encontrado", HttpStatus.UNAUTHORIZED);
    }

    if (userExists.isBlocked()) {
      System.out.println("Esse usuário está bloqueado");
      return new ResponseEntity<String>("Esse usuário está bloqueado", HttpStatus.UNAUTHORIZED);
    }
    try {
      if (userExists.getTotalLogins() > 10) {
        System.out.println("Limite de tentativas atingido, por favor troque sua senha");
        return new ResponseEntity<String>("Limite de tentativas atingido, por favor troque sua senha", HttpStatus.UNAUTHORIZED);
      }
    } catch (Exception e) {
    }

    if (!userExists.getPassword().equals(user.getPassword())) {
      System.out.println("Senha incorreta");

      try {
        userExists.setTotalFails(userExists.getTotalFails() + 1);
      } catch (Exception e) {
        userExists.setTotalFails(1);
      }

      if (userExists.getTotalFails() > 5) {
        userExists.setBlocked(true);
        System.out.println("Usuário bloqueado");
      }

      dao.save(userExists);
      return new ResponseEntity<String>("Senha incorreta",
          HttpStatus.UNAUTHORIZED);
    }

    try {
      userExists.setTotalLogins(userExists.getTotalLogins() + 1);
    } catch (Exception e) {
      userExists.setTotalLogins(1);
    }

    dao.save(userExists);
    return new ResponseEntity<String>("Login efetuado", HttpStatus.OK);
  }

  @GetMapping("/bloqueados")
  public ResponseEntity<UsuarioBean[]> usuariosBloqueados() {
    UsuarioBean[] bloqueados = dao.findByBlockedTrue();

    System.out.println(bloqueados.length);

    if (bloqueados.length > 0) {
      return new ResponseEntity<UsuarioBean[]>(bloqueados, HttpStatus.OK);
    } else {
      System.out.println("Não há usuários bloqueados");
      return new ResponseEntity<UsuarioBean[]>(HttpStatus.NO_CONTENT);
    }
  }

  @PutMapping("/trocasenha")
  public ResponseEntity<String> trocaSenha(@RequestBody TrocaSenhaBean user) {
    UsuarioBean userExists = dao.findByUsername(user.getUsername());

    if (userExists == null) {
      System.out.println("Usuário não existe");
      return new ResponseEntity<String>("Usuário não existe", HttpStatus.NOT_FOUND);
    }

    if (userExists.isBlocked()) {
      System.out.println("Usuário bloqueado");
      return new ResponseEntity<String>("Usuário bloqueado", HttpStatus.UNAUTHORIZED);
    }

    if (!userExists.getPassword().equals(user.getCurrentPassword())) {
      System.out.println("Senha atual incorreta");
      return new ResponseEntity<String>("Senha atual incorreta", HttpStatus.UNAUTHORIZED);
    }

    if (userExists.getPassword().equals(user.getNewPassword())) {
      System.out.println("Senha atual e nova são iguais");
      return new ResponseEntity<String>("Senha atual e nova são iguais", HttpStatus.BAD_REQUEST);
    }

    userExists.setPassword(user.getNewPassword());
    userExists.setTotalLogins(0);
    dao.save(userExists);
    return new ResponseEntity<String>("Senha alterada", HttpStatus.OK);
  }

@PostMapping("/desbloquear/{username}")
@PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<String> unblockUser(@PathVariable String username) {
    return username == null ? 
        ResponseEntity.badRequest().body("Deve existir um usuário") : 
        Optional.ofNullable(dao.findByUsername(username))
            .map(user -> !user.isBlocked() ? 
                ResponseEntity.badRequest().body("Usuário não está bloqueado") : 
                desbloquearUsuario(user))
            .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário inexistente"));
  }

  private ResponseEntity<String> desbloquearUsuario(UsuarioBean user) {
    user.setTotalFails(0);
    user.setBlocked(false);
    dao.save(user);
    return ResponseEntity.ok("Usuário foi desbloqueado");
  }
}