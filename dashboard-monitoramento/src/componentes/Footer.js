import React from 'react';
import '../estilos/Footer.css';

const Footer = () => {
  return (
    <footer className="footer">
      <div className="footer-content">
        <p className="footer-text">
          <i className="fas fa-code"></i>
          Desenvolvido com ❤️ por
          <a href="https://github.com/valentelucass" target="_blank">@valentelucass</a>
        </p>
      </div>
    </footer>
  );
};

export default Footer;